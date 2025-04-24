package terraformbuilder.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import components.TerraformGenerationHandler
import kotlinx.coroutines.launch
import terraformbuilder.ResourceType
import terraformbuilder.github.GithubService
import terraformbuilder.github.GithubUrlParser
import terraformbuilder.terraform.LocalDirectoryLoader
import terraformbuilder.terraform.TerraformParser
import terraformbuilder.terraform.TerraformVariable
import terraformbuilder.terraform.VariableState
import java.util.*

// Generate default names for resources
private fun generateDefaultName(resourceType: ResourceType): String {
    val randomId = UUID.randomUUID().toString().substring(0, 4)

    val prefix = resourceType.displayName.lowercase(Locale.getDefault()).replace(" ", "-")
    return "$prefix-$randomId"
}

// Format resource name for Terraform compatibility
// Should match the format used in TerraformCodeGenerator.formatResourceName
fun formatResourceName(content: String): String {
    // Convert to valid Terraform resource name: lowercase, underscores, no special chars
    return content.lowercase().replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
}

@Composable
fun editor(
    onNewProject: (String, String) -> Unit,
    onOpenProject: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveProjectAs: () -> Unit,
    onCloseProject: () -> Unit,
    onExit: () -> Unit,
    blockState: BlockState,
    variableState: VariableState
) {
    val viewState = remember { EditorViewState() }
    var editingChildBlockId by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current.density
    val focusRequester = remember { FocusRequester() }

    // Template selection state - moved template state to viewState
    var selectedTemplateFactory by remember { mutableStateOf<((String, Offset) -> CompositeBlock)?>(null) }

    // Track when block content changes to force recomposition
    val blockContentVersion = remember { mutableStateOf(0) }
    var hoveredVariableName by remember { mutableStateOf<String?>(null) }

    // Error and loading state
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    val terraformGenerationHandler = remember { TerraformGenerationHandler() }

    // Calculate visible elements based on view state
    val visibleBlocks = remember(
        blockState.allBlocks,
        blockState.allComposites,
        viewState.mode,
        viewState.activeCompositeId,
        blockContentVersion.value
    ) {
        if (viewState.isInMainView()) {
            blockState.allBlocks
        } else {
            blockState.getCompositeChildren(viewState.activeCompositeId!!)
        }
    }

    val visibleComposites = remember(blockState.allComposites, viewState.mode) {
        if (viewState.isInMainView()) blockState.allComposites else emptyList()
    }

    // Initialize view - center on blocks if any exist
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()

        // If there are blocks, center on them
        if (visibleBlocks.isNotEmpty()) {
            viewState.centerOnBlocks(visibleBlocks, Size(800f, 600f))
        }
    }

    // Connection drag handling
    val onConnectionDragStart: (Block, ConnectionPointType) -> Unit = { block, pointType ->
        blockState.startConnectionDrag(block, pointType)
        val connectionPoint = block.getConnectionPointPosition(pointType)
        blockState.updateDragPosition(connectionPoint)
        viewState.startMouseDown()
    }

    // Helper function to create default blocks
    val createBlockWithDefaults: (Block) -> Unit = { block ->
        val resourceType = block.resourceType
        val newBlock = block.copy(
            id = UUID.randomUUID().toString(),
            content = generateDefaultName(resourceType),
            resourceType = resourceType,
            description = block.description
        )
        blockState.addBlock(newBlock)
    }

    // Dialog components
    if (viewState.showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { viewState.hideDialog(EditorViewState.DialogType.TEMPLATE) },
            title = { Text("Create ${viewState.selectedTemplateName}") },
            text = {
                OutlinedTextField(
                    value = viewState.templateName,
                    onValueChange = { viewState.updateTemplateName(it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedTemplateFactory?.let { factory ->
                            // Calculate position at the center of the current view
                            val viewportCenter = Offset(
                                (800f / 2f - viewState.panOffset.x) / viewState.scale,
                                (600f / 2f - viewState.panOffset.y) / viewState.scale
                            )

                            // Create the composite
                            val composite = factory(viewState.templateName, viewportCenter)

                            // Add it to the state
                            blockState.addCompositeBlock(composite)

                            // Select the new composite
                            viewState.selectComposite(composite.id)
                        }
                        viewState.hideDialog(EditorViewState.DialogType.TEMPLATE)
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewState.hideDialog(EditorViewState.DialogType.TEMPLATE) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (viewState.showGroupDialog) {
        AlertDialog(
            onDismissRequest = { viewState.hideDialog(EditorViewState.DialogType.GROUP) },
            title = { Text("Create Group") },
            text = {
                OutlinedTextField(
                    value = viewState.groupNameInput,
                    onValueChange = { viewState.updateGroupName(it) },
                    label = { Text("Group Name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        blockState.groupBlocks(viewState.selectedBlockIds, viewState.groupNameInput)
                        viewState.clearMultiSelection()
                        viewState.hideDialog(EditorViewState.DialogType.GROUP)
                    }
                ) {
                    Text("Create Group")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewState.hideDialog(EditorViewState.DialogType.GROUP) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (viewState.showGithubDialog) {
        GithubUrlDialog(
            onDismiss = {
                viewState.hideDialog(EditorViewState.DialogType.GITHUB)
                errorMessage = null
            },
            onConfirm = { url ->
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        println("APP: Processing GitHub URL: $url")
                        val repoInfo = GithubUrlParser.parse(url)
                        if (repoInfo != null) {
                            println("APP: Parsed repo info: $repoInfo")

                            // Clear existing blocks and variables before loading new ones
                            blockState.clearAll()
                            variableState.clearAll()

                            val githubService = GithubService()
                            val files = githubService.loadTerraformFiles(repoInfo)

                            if (files.isEmpty()) {
                                errorMessage = "No Terraform files found in repository"
                                return@launch
                            }

                            println("APP: Loaded ${files.size} files")

                            val parser = TerraformParser()

                            // Parse all files
                            files.forEach { file ->
                                val parseResult = parser.parse(file)

                                // Add variables
                                parseResult.variables.forEach { variable ->
                                    println("APP: Adding variable ${variable.name}")
                                    variableState.addVariable(variable)
                                }

                                // Convert and add resources
                                val blocks = parser.convertToBlocks(parseResult.resources)
                                blocks.forEachIndexed { index, block ->
                                    val row = index / 3
                                    val col = index % 3
                                    val position = Offset(
                                        50f + col * 100f,
                                        50f + row * 50f
                                    )
                                    println("APP: Adding block ${block.content} at position $position")
                                    blockState.addBlock(block.copy(_position = position))
                                }
                            }

                            if (blockState.allBlocks.isEmpty() && variableState.variables.isEmpty()) {
                                errorMessage = "No Terraform resources or variables found in files"
                                return@launch
                            }

                            viewState.hideDialog(EditorViewState.DialogType.GITHUB)
                        } else {
                            errorMessage = "Invalid GitHub URL format"
                        }
                    } catch (e: Exception) {
                        println("APP: Error loading Terraform: ${e.message}")
                        e.printStackTrace()
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }

    if (viewState.showLocalDirectoryDialog) {
        loadDirectoryDialog(
            onDismiss = {
                viewState.hideDialog(EditorViewState.DialogType.LOCAL_DIRECTORY)
                errorMessage = null
            },
            onSelectDirectory = { directory ->
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        println("APP: Processing local directory: ${directory.absolutePath}")

                        // Clear existing blocks and variables before loading new ones
                        blockState.clearAll()
                        variableState.clearAll()

                        val directoryLoader = LocalDirectoryLoader()
                        val files = directoryLoader.loadTerraformFiles(directory)

                        if (files.isEmpty()) {
                            errorMessage = "No Terraform files found in directory"
                            return@launch
                        }

                        println("APP: Loaded ${files.size} files from local directory")

                        val parser = TerraformParser()

                        // Parse all files
                        files.forEach { file ->
                            val parseResult = parser.parse(file)

                            // Add variables
                            parseResult.variables.forEach { variable ->
                                println("APP: Adding variable ${variable.name}")
                                variableState.addVariable(variable)
                            }

                            // Convert and add resources
                            val blocks = parser.convertToBlocks(parseResult.resources)
                            blocks.forEachIndexed { index, block ->
                                val row = index / 3
                                val col = index % 3
                                val position = Offset(
                                    50f + col * 100f,
                                    50f + row * 50f
                                )
                                println("APP: Adding block ${block.content} at position $position")
                                blockState.addBlock(block.copy(_position = position))
                            }
                        }

                        if (blockState.allBlocks.isEmpty() && variableState.variables.isEmpty()) {
                            errorMessage = "No Terraform resources or variables found in files"
                            return@launch
                        }

                        viewState.hideDialog(EditorViewState.DialogType.LOCAL_DIRECTORY)
                    } catch (e: Exception) {
                        println("APP: Error loading Terraform: ${e.message}")
                        e.printStackTrace()
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }

    if (viewState.showVariablesDialog) {
        variableDialog(
            onDismiss = { viewState.hideDialog(EditorViewState.DialogType.VARIABLES) },
            variables = variableState.variables,
            onAddVariable = { variableState.addVariable(it) },
            onRemoveVariable = { variableState.removeVariable(it) },
            onUpdateVariable = { name, variable: TerraformVariable ->
                variableState.updateVariable(name, variable)
            }
        )
    }

    if (terraformGenerationHandler.showDialog.value) {
        showTerraformGenerationHandler(terraformGenerationHandler)
    }
    terraformGenerationHandler.fileDialogComponent(blockState, variableState, coroutineScope)

    // Main layout
    Column(modifier = Modifier.fillMaxSize()) {
        // File Menu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colors.primary)
        ) {
            fileMenu(
                onNewProject = onNewProject,
                onOpenProject = onOpenProject,
                onSaveProject = onSaveProject,
                onSaveProjectAs = onSaveProjectAs,
                onCloseProject = onCloseProject,
                onExit = onExit,
                isProjectOpen = true
            )
        }

        // Content area
        Row(modifier = Modifier.fillMaxSize()) {
            // Split the left sidebar into block library and template library
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(250.dp)
                    .background(Color.LightGray)
            ) {
                // Block library panel (top half)
                blockLibraryPanel(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    onBlockSelected = createBlockWithDefaults,
                    onGithubClick = { viewState.showDialog(EditorViewState.DialogType.GITHUB) },
                    onLocalDirectoryClick = { viewState.showDialog(EditorViewState.DialogType.LOCAL_DIRECTORY) },
                    onVariablesClick = { viewState.showDialog(EditorViewState.DialogType.VARIABLES) },
                    highlightVariableButton = hoveredVariableName != null,
                    onGenerateTerraformClick = {
                        terraformGenerationHandler.startGeneration()
                    }
                )

                Divider(color = Color.DarkGray, thickness = 1.dp)

                // Template library panel (bottom half)
                templateLibraryPanel(
                    onTemplateSelected = { name, factory ->
                        viewState.setTemplateSelection(name)
                        selectedTemplateFactory = factory
                        viewState.showDialog(EditorViewState.DialogType.TEMPLATE)
                    },
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            // Workspace Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clip(RectangleShape)
                    .focusRequester(focusRequester)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                // Find block at click position
                                val workspacePoint = viewState.screenToWorkspace(offset, density)

                                // First check if we clicked on a composite
                                val compositeAtPoint = visibleComposites.find { composite ->
                                    val radius = 50f // Using 100dp diameter
                                    val center = composite.position + Offset(radius, radius)
                                    (workspacePoint - center).getDistance() <= radius
                                }

                                if (compositeAtPoint != null) {
                                    // Start dragging the composite
                                    viewState.startDrag(compositeAtPoint.id, offset)
                                    viewState.selectComposite(compositeAtPoint.id)
                                } else {
                                    // Check for regular blocks
                                    val blockAtPoint = visibleBlocks.find { block ->
                                        workspacePoint.x >= block.position.x &&
                                                workspacePoint.x <= block.position.x + block.size.x &&
                                                workspacePoint.y >= block.position.y &&
                                                workspacePoint.y <= block.position.y + block.size.y
                                    }

                                    if (blockAtPoint != null) {
                                        // Start dragging the block
                                        viewState.startDrag(blockAtPoint.id, offset)
                                        viewState.selectBlock(blockAtPoint.id)
                                    } else {
                                        // Start panning
                                        viewState.startPanning(offset)
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                if (viewState.isPanning) {
                                    // Panning mode - update the pan offset
                                    viewState.updatePan(dragAmount.toDpOffset(density))
                                } else if (viewState.draggedBlockId != null && viewState.dragStartPosition != null) {
                                    // Check if we're dragging a composite or regular block
                                    val composite = visibleComposites.find { it.id == viewState.draggedBlockId }
                                    if (composite != null) {
                                        // Dragging a composite block
                                        val dragAmountInDp = dragAmount.toDpOffset(density) / viewState.scale
                                        val newPosition = composite.position + dragAmountInDp
                                        // Update composite position
                                        composite.position = newPosition
                                    } else {
                                        // Dragging a regular block
                                        val block = visibleBlocks.find { it.id == viewState.draggedBlockId }
                                        if (block != null) {
                                            val dragAmountInDp = dragAmount.toDpOffset(density) / viewState.scale
                                            val newPosition = block.position + dragAmountInDp
                                            blockState.updateBlockPosition(block.id, newPosition)
                                        }
                                    }
                                    viewState.updateDrag(change.position, dragAmount)
                                }
                            },
                            onDragEnd = {
                                // Keep selection state
                                if (viewState.dragStartBlockId != null) {
                                    // Check if it's a composite or regular block
                                    if (visibleComposites.any { it.id == viewState.dragStartBlockId }) {
                                        viewState.selectComposite(viewState.dragStartBlockId)
                                    } else {
                                        viewState.selectBlock(viewState.dragStartBlockId)
                                    }
                                }
                                viewState.endDrag()
                            },
                            onDragCancel = {
                                viewState.cancelDrag()
                            }
                        )
                    }
            ) {
                // Navigation breadcrumb if we're inside a composite
                if (viewState.isInCompositeView()) {
                    val composite = blockState.allComposites.find { it.id == viewState.activeCompositeId }
                    if (composite != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewState.exitToMainView() }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to main view")
                                Spacer(Modifier.width(4.dp))
                                Text("Back")
                            }

                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = "Editing: ${composite.name}",
                                style = MaterialTheme.typography.h6
                            )
                        }
                    }
                }

                // Group/ungroup action buttons
                if (viewState.selectedBlockIds.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                // Show group dialog
                                viewState.updateGroupName("New Group")
                                viewState.showDialog(EditorViewState.DialogType.GROUP)
                            }
                        ) {
                            Icon(Icons.Default.Group, "Group selected blocks")
                        }
                    }
                } else if (viewState.selectedCompositeId != null) {
                    // Ungroup button for selected composite
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                blockState.ungroupComposite(viewState.selectedCompositeId!!)
                                viewState.setNoneSelected()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore, // Use an appropriate icon
                                contentDescription = "Ungroup"
                            )
                        }
                    }
                }

                // This Box applies the transformation
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer(
                            translationX = viewState.panOffset.x,
                            translationY = viewState.panOffset.y,
                            scaleX = viewState.scale,
                            scaleY = viewState.scale
                        )
                ) {
                    // Draw grid pattern
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridSize = 50f
                        val gridColor = Color(0xFFE0E0E0)

                        // Draw grid lines...
                        var x = 0f
                        while (x < size.width) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1f,
                                cap = StrokeCap.Round
                            )
                            x += gridSize
                        }

                        var y = 0f
                        while (y < size.height) {
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f,
                                cap = StrokeCap.Round
                            )
                            y += gridSize
                        }
                    }

                    // Draw connections between blocks
                    connectionCanvas(
                        connections = blockState.connections,
                        dragState = blockState.dragState,
                        blocks = visibleBlocks,
                        modifier = Modifier.fillMaxSize()
                    )

                    // If we're in the main view, render composite blocks and regular blocks
                    if (viewState.isInMainView()) {
                        // Draw regular blocks
                        visibleBlocks.forEach { block ->
                            blockView(
                                block = block,
                                onDragEnd = { newPosition ->
                                    blockState.updateBlockPosition(block.id, newPosition)
                                },
                                onDragStart = { },
                                onRename = { newContent ->
                                    println("WORKSPACE: Renaming block ${block.id} from '${block.content}' to '$newContent'")
                                    blockState.updateBlockContent(block.id, newContent)
                                    blockContentVersion.value++
                                    println("WORKSPACE: Incremented block content version to ${blockContentVersion.value}")
                                },
                                onConnectionDragStart = onConnectionDragStart,
                                onUpdateBlockSize = { blockId, size ->
                                    blockState.updateBlockSize(blockId, size)
                                },
                                onBlockSelected = { blockId ->
                                    viewState.selectBlock(blockId)
                                    println("Block selected through click: $blockId")
                                },
                                isHovered = false,
                                isReferenced = blockState.isBlockReferenced(block.id),
                                activeDragState = if (blockState.dragState.isActive) blockState.dragState else null
                            )
                        }

                        // Draw composite blocks
                        visibleComposites.forEach { composite ->
                            // Debug log
                            println("Rendering composite: ${composite.name} at ${composite.position}")

                            compositeBlockView(
                                compositeBlock = composite,
                                onDragStart = { /* Handle drag start */ },
                                onDragEnd = { newPosition ->
                                    // Update composite position
                                    composite.position = newPosition
                                },
                                onRename = { newName ->
                                    // Update composite name
                                    composite.name = newName
                                },
                                onBlockSelected = { id ->
                                    viewState.selectComposite(id)
                                },
                                isSelected = viewState.selectedCompositeId == composite.id
                            )
                        }
                    } else {
                        // We're inside a composite, show its children
                        val composite = blockState.allComposites.find { it.id == viewState.activeCompositeId }
                        composite?.children?.forEach { childBlock ->
                            blockView(
                                block = childBlock,
                                onDragEnd = { newPosition ->
                                    blockState.updateBlockPosition(childBlock.id, newPosition)
                                },
                                onDragStart = { },
                                onRename = { newContent ->
                                    blockState.updateBlockContent(childBlock.id, newContent)
                                    blockContentVersion.value++
                                },
                                onConnectionDragStart = onConnectionDragStart,
                                onUpdateBlockSize = { blockId, size ->
                                    blockState.updateBlockSize(blockId, size)
                                },
                                onBlockSelected = { blockId ->
                                    editingChildBlockId = blockId
                                },
                                isHovered = false,
                                isReferenced = false,
                                activeDragState = if (blockState.dragState.isActive) blockState.dragState else null
                            )
                        }
                    }
                }

                // Property panel - show different content based on selection
                when {
                    // Editing a composite block
                    viewState.selectedCompositeId != null -> {
                        val composite = visibleComposites.find { it.id == viewState.selectedCompositeId }
                        composite?.let {
                            compositePropertyEditorPanel(
                                compositeBlock = it,
                                onPropertyChange = { name, value ->
                                    // Update property
                                    it.setProperty(name, value)
                                },
                                onRename = { newName ->
                                    // Rename composite
                                    it.name = newName
                                },
                                onEditChild = { childId ->
                                    // Enter the composite to edit its children
                                    viewState.enterComposite(it.id)
                                    editingChildBlockId = childId
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            )
                        }
                    }

                    // Editing a regular block
                    viewState.selectedBlockId != null -> {
                        val block = visibleBlocks.find { it.id == viewState.selectedBlockId }
                        block?.let {
                            propertyEditorPanel(
                                block = it,
                                onPropertyChange = { propertyName, propertyValue ->
                                    blockState.updateBlockProperty(it.id, propertyName, propertyValue)
                                },
                                onNavigateToVariable = { variableName ->
                                    hoveredVariableName = variableName
                                    viewState.showDialog(EditorViewState.DialogType.VARIABLES)
                                },
                                onNavigateToResource = { resourceType, resourceName ->
                                    // Find and select referenced resource
                                    blockState.allBlocks.find { b ->
                                        b.resourceType.resourceName == resourceType &&
                                                formatResourceName(b.content) == resourceName
                                    }?.let { foundBlock ->
                                        viewState.selectBlock(foundBlock.id)
                                        viewState.centerOn(foundBlock.position, Offset(400f, 300f))
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            )
                        }
                    }

                    // Editing a child block inside a composite
                    editingChildBlockId != null -> {
                        val currentComposite = blockState.allComposites.find { it.id == viewState.activeCompositeId }
                        val childBlock = currentComposite?.children?.find { it.id == editingChildBlockId }
                        childBlock?.let {
                            propertyEditorPanel(
                                block = it,
                                onPropertyChange = { propertyName, propertyValue ->
                                    blockState.updateBlockProperty(it.id, propertyName, propertyValue)
                                },
                                onNavigateToVariable = { variableName ->
                                    hoveredVariableName = variableName
                                    viewState.showDialog(EditorViewState.DialogType.VARIABLES)
                                },
                                onNavigateToResource = { resourceType, resourceName ->
                                    // Handle resource navigation within the composite
                                    currentComposite.children.find { b ->
                                        b.resourceType.resourceName == resourceType &&
                                                formatResourceName(b.content) == resourceName
                                    }?.let { foundBlock ->
                                        editingChildBlockId = foundBlock.id
                                        viewState.centerOn(foundBlock.position, Offset(400f, 300f))
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun showTerraformGenerationHandler(terraformGenerationHandler: TerraformGenerationHandler) {
    AlertDialog(
        onDismissRequest = { terraformGenerationHandler.closeDialog() },
        title = { Text("Generate Terraform Code") },
        text = {
            if (terraformGenerationHandler.isGenerating.value) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Generating Terraform code...")
                }
            } else {
                Text(
                    terraformGenerationHandler.resultMessage.value
                        ?: "Choose a directory where Terraform files will be generated."
                )
            }
        },
        confirmButton = {
            if (!terraformGenerationHandler.isGenerating.value && terraformGenerationHandler.resultMessage.value == null) {
                Button(onClick = { terraformGenerationHandler.showFileSelector() }) {
                    Text("Select Directory")
                }
            } else {
                TextButton(onClick = { terraformGenerationHandler.closeDialog() }) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (!terraformGenerationHandler.isGenerating.value && terraformGenerationHandler.resultMessage.value == null) {
                TextButton(onClick = { terraformGenerationHandler.closeDialog() }) {
                    Text("Cancel")
                }
            }
        }
    )
}