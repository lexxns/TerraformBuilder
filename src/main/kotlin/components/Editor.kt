package terraformbuilder.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
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
import terraformbuilder.terraform.*
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
@Preview
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
    var selectedBlock by remember { mutableStateOf<Block?>(null) }
    val density = LocalDensity.current.density

    // Add pan and zoom state
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    val focusRequester = remember { FocusRequester() }

    // Track when the mouse button is down during a drag
    var isMouseDown by remember { mutableStateOf(false) }
    var isPanning by remember { mutableStateOf(false) }
    var lastPanPosition by remember { mutableStateOf(Offset.Zero) }
    var draggedBlockId by remember { mutableStateOf<String?>(null) }
    var dragStartPosition by remember { mutableStateOf<Offset?>(null) }
    var dragStartBlockId by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // Force recomposition when any block content changes
    val blockContentVersion = remember { mutableStateOf(0) }

    // Handle keyboard events for panning and zooming
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()

        // Force an initial composition/draw cycle
        panOffset = Offset.Zero
        scale = 1f

        // If there are blocks, center on them
        if (blockState.blocks.isNotEmpty()) {
            // Find the center of all blocks
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            blockState.blocks.forEach { block ->
                val left = block.position.x
                val top = block.position.y
                val right = left + block.size.x
                val bottom = top + block.size.y

                minX = minOf(minX, left)
                minY = minOf(minY, top)
                maxX = maxOf(maxX, right)
                maxY = maxOf(maxY, bottom)
            }

            // Center the view on the blocks
            if (minX != Float.MAX_VALUE) {
                val centerX = (minX + maxX) / 2
                val centerY = (minY + maxY) / 2

                // Set initial pan to center blocks
                panOffset = Offset(
                    -centerX + 400,
                    -centerY + 300
                )
            }
        }
    }

    // Helper function to convert screen coordinates to workspace coordinates
    fun screenToWorkspace(screenPos: Offset): Offset {
        // First convert to dp
        val dpPos = screenPos.toDpOffset(density)
        // Then apply inverse transform (pan and scale)
        return (dpPos - panOffset) / scale
    }

    // Helper function to calculate drag amount in workspace coordinates
    fun calculateWorkspaceDragAmount(currentPos: Offset, startPos: Offset): Offset {
        val currentWorkspace = screenToWorkspace(currentPos)
        val startWorkspace = screenToWorkspace(startPos)
        return currentWorkspace - startWorkspace
    }

    // When blocks start a connection, update our state
    val onConnectionDragStart: (Block, ConnectionPointType) -> Unit = { block, pointType ->
        // Start the connection
        blockState.startConnectionDrag(block, pointType)
        val connectionPoint = block.getConnectionPointPosition(pointType)
        blockState.updateDragPosition(connectionPoint)
        isMouseDown = true
    }

    var showGithubDialog by remember { mutableStateOf(false) }
    var showLocalDirectoryDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showVariablesDialog by remember { mutableStateOf(false) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var hoveredVariableName by remember { mutableStateOf<String?>(null) }
    val terraformGenerationHandler = remember { TerraformGenerationHandler() }

    if (showGithubDialog) {
        GithubUrlDialog(
            onDismiss = {
                showGithubDialog = false
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

                            if (blockState.blocks.isEmpty() && variableState.variables.isEmpty()) {
                                errorMessage = "No Terraform resources or variables found in files"
                                return@launch
                            }

                            showGithubDialog = false
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

    if (showLocalDirectoryDialog) {
        loadDirectoryDialog(
            onDismiss = {
                showLocalDirectoryDialog = false
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

                        if (blockState.blocks.isEmpty() && variableState.variables.isEmpty()) {
                            errorMessage = "No Terraform resources or variables found in files"
                            return@launch
                        }

                        showLocalDirectoryDialog = false
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

    if (showVariablesDialog) {
        variableDialog(
            onDismiss = { showVariablesDialog = false },
            variables = variableState.variables,
            onAddVariable = { variableState.addVariable(it) },
            onRemoveVariable = { variableState.removeVariable(it) },
            onUpdateVariable = { name, variable: TerraformVariable -> variableState.updateVariable(name, variable) }
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
            // Block Library Panel
            blockLibraryPanel(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(250.dp)
                    .background(Color.LightGray)
                    .padding(8.dp),
                onBlockSelected = { block ->
                    // Create a copy with a unique ID and a default name based on the resource type
                    val resourceType = block.resourceType
                    selectedBlock = block.copy(
                        id = UUID.randomUUID().toString(),
                        content = generateDefaultName(resourceType),
                        resourceType = resourceType,
                        description = block.description
                    )
                },
                onGithubClick = { showGithubDialog = true },
                onLocalDirectoryClick = { showLocalDirectoryDialog = true },
                onVariablesClick = { showVariablesDialog = true },
                highlightVariableButton = hoveredVariableName != null,
                onGenerateTerraformClick = {
                    terraformGenerationHandler.startGeneration()
                }
            )

            // Workspace Area
            // Modified pointerInput for stable panning
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clip(RectangleShape)
                    .focusRequester(focusRequester)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isMouseDown = true
                                isDragging = false
                                dragStartPosition = offset

                                // Find block at click position
                                val workspacePoint = screenToWorkspace(offset)
                                val blockAtPoint = blockState.blocks.find { block ->
                                    workspacePoint.x >= block.position.x &&
                                            workspacePoint.x <= block.position.x + block.size.x &&
                                            workspacePoint.y >= block.position.y &&
                                            workspacePoint.y <= block.position.y + block.size.y
                                }

                                if (blockAtPoint != null) {
                                    // Start dragging the block - LOCK the mode to block dragging
                                    draggedBlockId = blockAtPoint.id
                                    dragStartBlockId = blockAtPoint.id
                                    selectedBlockId = blockAtPoint.id
                                    isPanning = false  // Ensure we're not in panning mode
                                } else {
                                    // Start panning - LOCK the mode to panning
                                    isPanning = true
                                    lastPanPosition = offset
                                    // Clear any block dragging state
                                    draggedBlockId = null
                                    dragStartBlockId = null
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                isDragging = true

                                // Use completely separate code paths based on the initial mode
                                if (isPanning) {
                                    // We're in panning mode - just update the pan offset directly
                                    // Don't recalculate anything or check for blocks
                                    panOffset += dragAmount
                                } else if (draggedBlockId != null && dragStartPosition != null) {
                                    // We're in block dragging mode
                                    val block = blockState.blocks.find { it.id == draggedBlockId }
                                    if (block != null) {
                                        val workspaceDragAmount =
                                            calculateWorkspaceDragAmount(change.position, dragStartPosition!!)
                                        val newPosition = block.position + workspaceDragAmount
                                        blockState.updateBlockPosition(block.id, newPosition)
                                        dragStartPosition = change.position
                                    }
                                }
                            },
                            onDragEnd = {
                                // Clean reset of all state
                                isMouseDown = false
                                isPanning = false
                                isDragging = false
                                draggedBlockId = null
                                dragStartPosition = null

                                // Keep selection state
                                if (dragStartBlockId != null) {
                                    selectedBlockId = dragStartBlockId
                                }
                                dragStartBlockId = null
                            },
                            onDragCancel = {
                                // Full reset
                                isMouseDown = false
                                isPanning = false
                                isDragging = false
                                draggedBlockId = null
                                dragStartPosition = null
                                dragStartBlockId = null
                            }
                        )
                    }
            ) {
                // This Box applies the transformation
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer(
                            translationX = panOffset.x,
                            translationY = panOffset.y,
                            scaleX = scale,
                            scaleY = scale
                        )
                ) {
                    // Draw grid pattern
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridSize = 50f
                        val gridColor = Color(0xFFE0E0E0)

                        // Draw vertical lines
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

                        // Draw horizontal lines
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
                        blocks = blockState.blocks,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Draw blocks
                    for (block in blockState.blocks) {
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
                                selectedBlockId = blockId
                                println("Block selected through click: $blockId")
                            },
                            isHovered = false,
                            isReferenced = blockState.isBlockReferenced(block.id),
                            activeDragState = if (blockState.dragState.isActive) blockState.dragState else null
                        )
                    }

                    // Add new block when selected
                    LaunchedEffect(selectedBlock) {
                        selectedBlock?.let { block ->
                            // Get the description from the schema
                            val description = TerraformProperties.getResourceDescription(block.resourceType)
                            blockState.addBlock(block.copy(description = description))
                            selectedBlock = null
                        }
                    }
                }

                // Display property editor for selected block
                selectedBlockId?.let { id ->
                    key(id, blockContentVersion.value) {
                        val block = blockState.blocks.find { it.id == id }
                        block?.let {
                            // Property editor panel
                            propertyEditorPanel(
                                block = block,
                                onPropertyChange = { propertyName, propertyValue ->
                                    blockState.updateBlockProperty(id, propertyName, propertyValue)
                                },
                                onNavigateToVariable = { variableName ->
                                    // Show variables dialog focused on this variable
                                    hoveredVariableName = variableName
                                    showVariablesDialog = true
                                },
                                onNavigateToResource = { resourceType, resourceName ->
                                    // Find and highlight the referenced resource
                                    blockState.blocks.find { b ->
                                        b.resourceType.resourceName == resourceType &&
                                                formatResourceName(b.content) == resourceName
                                    }?.let { foundBlock ->
                                        // Highlight/select the found block
                                        selectedBlockId = foundBlock.id

                                        // Pan to center the found block
                                        panOffset = Offset(
                                            -foundBlock.position.x + 400,
                                            -foundBlock.position.y + 300
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

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
