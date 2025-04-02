package terraformbuilder.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import terraformbuilder.ResourceType
import terraformbuilder.github.GithubService
import terraformbuilder.github.GithubUrlParser
import terraformbuilder.terraform.*
import java.util.*

// Library block creation helper
private fun createLibraryBlock(type: BlockType, resourceType: ResourceType): Block {
    return createBlock(
        id = UUID.randomUUID().toString(),
        type = type,
        content = resourceType.displayName,
        resourceType = resourceType
    )
}

// Generate default names for resources
private fun generateDefaultName(resourceType: ResourceType): String {
    val randomId = UUID.randomUUID().toString().substring(0, 4)

    val prefix = resourceType.displayName.lowercase(Locale.getDefault()).replace(" ", "-")
    return "$prefix-$randomId"
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
    var hoverPosition by remember { mutableStateOf(Offset.Zero) }
    var hoverDpPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density

    // Add pan and zoom state
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    val focusRequester = remember { FocusRequester() }

    // Track when the mouse button is down during a drag
    var isMouseDown by remember { mutableStateOf(false) }

    // Force recomposition when any block content changes
    val blockContentVersion = remember { mutableStateOf(0) }
    
    var isPanning by remember { mutableStateOf(false) }
    var lastPanPosition by remember { mutableStateOf(Offset.Zero) }


    // Handle keyboard events for panning and zooming
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showVariablesDialog by remember { mutableStateOf(false) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var hoveredBlockId by remember { mutableStateOf<String?>(null) }

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

    if (showVariablesDialog) {
        variableDialog(
            onDismiss = { showVariablesDialog = false },
            variables = variableState.variables,
            onAddVariable = { variableState.addVariable(it) },
            onRemoveVariable = { variableState.removeVariable(it) },
            onUpdateVariable = { name, variable: TerraformVariable -> variableState.updateVariable(name, variable) }
        )
    }

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
                        description = block.description // Keep the description from the library block
                    )
                },
                onGithubClick = { showGithubDialog = true },
                onVariablesClick = { showVariablesDialog = true }
            )

            // Workspace Area - the key is to have this area properly clipped
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clip(RectangleShape) // Ensure clipping
                    .focusRequester(focusRequester)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull()

                                if (pointer != null) {
                                    // Update hover position regardless
                                    hoverPosition = pointer.position
                                    hoverDpPosition = pointer.position.toDpOffset(density)

                                    when {
                                        // Mouse pressed - start panning or connection drag
                                        pointer.pressed && !isPanning && !blockState.dragState.isActive -> {
                                            // Check if we're about to start a connection drag (e.g., over a connection point)
                                            val connectionDrag =
                                                false // Your logic to determine if this is a connection drag

                                            if (!connectionDrag) {
                                                isPanning = true
                                                lastPanPosition = pointer.position
                                            }
                                        }

                                        // Mouse moved while pressed - handle panning
                                        pointer.pressed && isPanning -> {
                                            val dragAmount = pointer.position - lastPanPosition

                                            // Apply panning with increased speed
                                            panOffset += Offset(dragAmount.x * 3f, dragAmount.y * 3f)

                                            // Update last position
                                            lastPanPosition = pointer.position
                                        }

                                        // Mouse released - end panning
                                        !pointer.pressed && isPanning -> {
                                            isPanning = false
                                        }

                                        // Connection drag handling
                                        blockState.dragState.isActive -> {
                                            blockState.updateDragPosition(hoverDpPosition)

                                            if (!pointer.pressed) {
                                                blockState.endConnectionDrag(hoverDpPosition)
                                            }
                                        }
                                    }

                                    // Consume the event
                                    pointer.consume()
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            // Handle zoom
                            val oldScale = scale
                            scale = (scale * zoom).coerceIn(0.1f, 5f)

                            // Adjust pan offset to maintain zoom point
                            val newScaleFactor = scale / oldScale
                            val centroidOffset = centroid - panOffset
                            panOffset = centroid - (centroidOffset * newScaleFactor)
                        }
                    }
                    // Add separate pointer input for mouse moves without dragging
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.first().position

                                // Update hover position on any mouse movement
                                hoverPosition = position
                                hoverDpPosition = position.toDpOffset(density)

                                // If we're dragging a connection, update its position
                                if (blockState.dragState.isActive && isMouseDown) {
                                    blockState.updateDragPosition(hoverDpPosition)
                                }
                            }
                        }
                    }
                    // Handle keyboard events for panning and zooming
                    .onKeyEvent { keyEvent ->
                        // Much faster panning with keyboard (100 units instead of 20)
                        val panAmount = 100f

                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                // Create a brand new Offset to ensure state change detection
                                panOffset = Offset(panOffset.x + panAmount, panOffset.y)
                                true
                            }

                            Key.DirectionRight -> {
                                // Create a brand new Offset to ensure state change detection
                                panOffset = Offset(panOffset.x - panAmount, panOffset.y)
                                true
                            }

                            Key.DirectionUp -> {
                                // Create a brand new Offset to ensure state change detection
                                panOffset = Offset(panOffset.x, panOffset.y + panAmount)
                                true
                            }

                            Key.DirectionDown -> {
                                // Create a brand new Offset to ensure state change detection
                                panOffset = Offset(panOffset.x, panOffset.y - panAmount)
                                true
                            }

                            Key.Minus -> {
                                // Create a brand new Offset to ensure state change detection
                                var newScale = (scale * 0.9f).coerceIn(0.1f, 5f)
                                // Only update if there's a meaningful change
                                if (newScale != scale) {
                                    scale = newScale
                                }
                                true
                            }

                            Key.Plus, Key.Equals -> {
                                // Create a brand new Offset to ensure state change detection
                                var newScale = (scale * 1.1f).coerceIn(0.1f, 5f)
                                // Only update if there's a meaningful change
                                if (newScale != scale) {
                                    scale = newScale
                                }
                                true
                            }

                            else -> false
                        }
                    }
            ) {
                // This Box applies the transformation
                Box(
                    modifier = Modifier
                        .matchParentSize() // Fill the workspace area
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
                            // Pass hover state to block
                            isHovered = hoveredBlockId == block.id,
                            // Pass the active drag state to determine connection point visibility
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

@Composable
fun blockLibraryPanel(
    modifier: Modifier = Modifier,
    onBlockSelected: (Block) -> Unit,
    onGithubClick: () -> Unit,
    onVariablesClick: () -> Unit
) {
    var expandedCategories by remember { mutableStateOf(setOf<BlockType>()) }
    var searchQuery by remember { mutableStateOf("") }
    val categorizer = remember { ResourceTypeCategorizer() }
    val resourceCategories = remember {
        ResourceType.entries
            .filter { it != ResourceType.UNKNOWN }
            .groupBy { categorizer.determineBlockType(it) }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Add GitHub button as first item
        item {
            Button(
                onClick = onGithubClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("Load from GitHub")
            }

            Button(
                onClick = onVariablesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Manage Variables")
            }

            // Add search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = { Text("Search resources...") },
                singleLine = true
            )
        }

        // Add resource categories
        resourceCategories.forEach { (category, resources) ->
            // Filter resources based on search query
            val filteredResources = resources.filter { resourceType ->
                val matchesName = resourceType.displayName.contains(searchQuery, ignoreCase = true)
                val matchesDescription = TerraformProperties.getResourceDescription(resourceType)
                    .contains(searchQuery, ignoreCase = true)
                matchesName || matchesDescription
            }

            // Only show category if it has matching resources
            if (filteredResources.isNotEmpty()) {
                // Category header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedCategories = if (category in expandedCategories) {
                                    expandedCategories - category
                                } else {
                                    expandedCategories + category
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (category in expandedCategories) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (category in expandedCategories) {
                                "Collapse $category"
                            } else {
                                "Expand $category"
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.subtitle1
                        )
                    }
                }

                // Resource items if category is expanded
                if (category in expandedCategories) {
                    filteredResources.forEach { resourceType ->
                        val description = TerraformProperties.getResourceDescription(resourceType)
                        val block = createBlock(
                            id = UUID.randomUUID().toString(),
                            type = categorizer.determineBlockType(resourceType),
                            content = resourceType.displayName,
                            resourceType = resourceType,
                            description = description
                        )
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onBlockSelected(block) }
                                    .padding(vertical = 4.dp),
                                elevation = 2.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text(
                                        text = resourceType.displayName,
                                        style = MaterialTheme.typography.body1
                                    )
                                    if (description.isNotEmpty()) {
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.body2,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}