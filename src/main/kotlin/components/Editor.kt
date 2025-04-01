package terraformbuilder.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

    // Track when the mouse button is down during a drag
    var isMouseDown by remember { mutableStateOf(false) }

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
    Box(modifier = Modifier.fillMaxSize()) {
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
        ) {
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

            // Workspace Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                hoverPosition = offset
                                hoverDpPosition = offset.toDpOffset(density)
                                println("WORKSPACE: Drag started at position: $offset, dp: $hoverDpPosition")
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                hoverPosition = change.position
                                hoverDpPosition = change.position.toDpOffset(density)

                                // Update connection drag position if active
                                if (blockState.dragState.isActive) {
                                    blockState.updateDragPosition(hoverDpPosition)
                                }
                            },
                            onDragEnd = {
                                println("WORKSPACE: Drag ended at position: $hoverDpPosition")
                                // When the drag ends, complete any active connection
                                if (blockState.dragState.isActive) {
                                    blockState.endConnectionDrag(hoverDpPosition)
                                }
                                isMouseDown = false
                            }
                        )
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
            ) {
                workspaceArea(
                    modifier = Modifier.fillMaxSize(),
                    blockState = blockState,
                    hoverDpPosition = hoverDpPosition,
                    onConnectionDragStart = onConnectionDragStart
                )

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

@Composable
fun workspaceArea(
    modifier: Modifier = Modifier,
    blockState: BlockState,
    hoverDpPosition: Offset,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit
) {
    // State to track clicked positions for debugging
    var clickedPosition by remember { mutableStateOf<Offset?>(null) }
    var clickedDpPosition by remember { mutableStateOf<Offset?>(null) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var hoveredBlockId by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current.density

    // Force recomposition when any block content changes
    val blockContentVersion = remember { mutableStateOf(0) }

    // Check if mouse is hovering over any block (recalculate when mouse moves)
    LaunchedEffect(hoverDpPosition) {
        val newHoveredBlockId = blockState.blocks.find { block ->
            // Create an expanded hit area that includes connection points
            val connectionPointWidth = 16f // Width of connection points in dp
            val blockLeft = block.position.x - connectionPointWidth // Expand left to include input connection point
            val blockRight =
                block.position.x + block.size.x + connectionPointWidth // Expand right to include output connection point
            val blockTop = block.position.y
            val blockBottom = blockTop + block.size.y

            hoverDpPosition.x in blockLeft..blockRight && hoverDpPosition.y in blockTop..blockBottom
        }?.id

        // Only update if changed to avoid unnecessary recompositions
        if (hoveredBlockId != newHoveredBlockId) {
            hoveredBlockId = newHoveredBlockId
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8))
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    // Record clicked position for debugging
                    clickedPosition = position
                    clickedDpPosition = position.toDpOffset(density)

                    // Check if we clicked on a block for property editing
                    val dpPos = position.toDpOffset(density)
                    val clickedBlockId = blockState.blocks.find { block ->
                        // Check if the click (in dp) is within the block bounds (also in dp)
                        val blockLeft = block.position.x
                        val blockRight = blockLeft + block.size.x
                        val blockTop = block.position.y
                        val blockBottom = blockTop + block.size.y

                        dpPos.x in blockLeft..blockRight && dpPos.y in blockTop..blockBottom
                    }?.id

                    // Update selected block (null if clicked on empty space)
                    selectedBlockId = clickedBlockId

                    println("Workspace click: ${if (clickedBlockId != null) "Selected block $clickedBlockId" else "Deselected block"}")
                }
            }
    ) {
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
