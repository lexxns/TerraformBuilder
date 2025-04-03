package terraformbuilder.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
    var isPanning by remember { mutableStateOf(false) }
    var lastPanPosition by remember { mutableStateOf(Offset.Zero) }
    var draggedBlockId by remember { mutableStateOf<String?>(null) }
    var lastClickPosition by remember { mutableStateOf<Offset?>(null) }
    var dragStartPosition by remember { mutableStateOf<Offset?>(null) }
    var dragStartBlockId by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragThreshold by remember { mutableStateOf(5f) } // Minimum distance to consider a drag

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

    // Helper function to convert workspace coordinates to screen coordinates
    fun workspaceToScreen(workspacePos: Offset): Offset {
        // Apply transform (scale and pan)
        val screenPos = workspacePos * scale + panOffset
        // Convert to pixels
        return Offset(screenPos.x * density, screenPos.y * density)
    }

    fun isPointInBlock(workspacePoint: Offset, block: Block): Boolean {
        // Point is already in workspace coordinates
        return workspacePoint.x >= block.position.x &&
                workspacePoint.x <= block.position.x + block.size.x &&
                workspacePoint.y >= block.position.y &&
                workspacePoint.y <= block.position.y + block.size.y
    }

    fun findBlockAtPoint(point: Offset): Block? {
        // Convert point to workspace coordinates
        val workspacePoint = screenToWorkspace(point)
        return blockState.blocks.find { block -> isPointInBlock(workspacePoint, block) }
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
                        description = block.description
                    )
                },
                onGithubClick = { showGithubDialog = true },
                onVariablesClick = { showVariablesDialog = true }
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