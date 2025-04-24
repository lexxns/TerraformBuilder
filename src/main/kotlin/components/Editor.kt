package terraformbuilder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import terraformbuilder.ResourceType
import terraformbuilder.components.block.Block
import terraformbuilder.components.block.BlockState
import terraformbuilder.components.block.CompositeBlock
import terraformbuilder.components.block.ConnectionPointType
import terraformbuilder.components.dialog.editorDialogs
import terraformbuilder.components.gesture.editorGestureHandler
import terraformbuilder.components.gesture.handleConnectionDragStart
import terraformbuilder.components.properties.propertyPanelContainer
import terraformbuilder.components.sidebar.editorSidebar
import terraformbuilder.components.workspace.calculateVisibleBlocks
import terraformbuilder.components.workspace.calculateVisibleComposites
import terraformbuilder.components.workspace.editorWorkspace
import terraformbuilder.components.workspace.fileMenuBar
import terraformbuilder.terraform.TerraformGenerationHandler
import terraformbuilder.terraform.VariableState
import terraformbuilder.terraform.formatResourceName
import java.util.*

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
    var selectedTemplateFactory by remember { mutableStateOf<((String, Offset) -> CompositeBlock)?>(null) }
    var hoveredVariableName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val terraformGenerationHandler = remember { TerraformGenerationHandler() }

    // Calculate visible elements
    val visibleBlocks = calculateVisibleBlocks(blockState, viewState)
    val visibleComposites = calculateVisibleComposites(blockState, viewState)

    // Initialize view
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (visibleBlocks.isNotEmpty()) {
            viewState.centerOnBlocks(visibleBlocks, Size(800f, 600f))
        }
    }

    // Connection drag handling
    val onConnectionDragStart = { block: Block, pointType: ConnectionPointType ->
        handleConnectionDragStart(block, pointType, blockState, viewState)
    }

    // Helper function to create blocks with defaults
    val createBlockWithDefaults = { block: Block ->
        createDefaultBlock(block, blockState)
    }

    // Render dialogs
    editorDialogs(
        viewState = viewState,
        selectedTemplateFactory = selectedTemplateFactory,
        blockState = blockState,
        variableState = variableState,
        isLoading = isLoading,
        errorMessage = errorMessage,
        setIsLoading = { isLoading = it },
        setErrorMessage = { errorMessage = it },
        coroutineScope = coroutineScope,
        terraformGenerationHandler = terraformGenerationHandler
    )

    // Main layout
    Column(modifier = Modifier.fillMaxSize()) {
        // File Menu
        fileMenuBar(
            onNewProject = onNewProject,
            onOpenProject = onOpenProject,
            onSaveProject = onSaveProject,
            onSaveProjectAs = onSaveProjectAs,
            onCloseProject = onCloseProject,
            onExit = onExit
        )

        // Content area
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar with block and template libraries
            editorSidebar(
                onBlockSelected = createBlockWithDefaults,
                onTemplateSelected = { name, factory ->
                    viewState.setTemplateSelection(name)
                    selectedTemplateFactory = factory
                    viewState.showDialog(EditorViewState.DialogType.TEMPLATE)
                },
                onGithubClick = { viewState.showDialog(EditorViewState.DialogType.GITHUB) },
                onLocalDirectoryClick = { viewState.showDialog(EditorViewState.DialogType.LOCAL_DIRECTORY) },
                onVariablesClick = { viewState.showDialog(EditorViewState.DialogType.VARIABLES) },
                highlightVariableButton = hoveredVariableName != null,
                onGenerateTerraformClick = { terraformGenerationHandler.startGeneration() },
                modifier = Modifier.width(250.dp).fillMaxHeight()
            )

            // Main workspace
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clip(RectangleShape)
                    .focusRequester(focusRequester)
                    // Apply gesture handling
                    .then(
                        editorGestureHandler(
                            viewState = viewState,
                            blockState = blockState,
                            visibleBlocks = visibleBlocks,
                            visibleComposites = visibleComposites,
                            density = density
                        )
                    )
            ) {
                // Workspace content with blocks, connections, etc.
                editorWorkspace(
                    viewState = viewState,
                    blockState = blockState,
                    visibleBlocks = visibleBlocks,
                    visibleComposites = visibleComposites,
                    onConnectionDragStart = onConnectionDragStart,
                    onBlockSelected = { viewState.selectBlock(it) },
                    onCompositeSelected = { viewState.selectComposite(it) },
                    onUpdateBlockPosition = { id, pos -> blockState.updateBlockPosition(id, pos) },
                    onUpdateBlockContent = { id, content -> blockState.updateBlockContent(id, content) },
                    onUpdateBlockSize = { id, size -> blockState.updateBlockSize(id, size) }
                )

                // Property panel based on selection
                propertyPanelContainer(
                    viewState = viewState,
                    blockState = blockState,
                    visibleBlocks = visibleBlocks,
                    visibleComposites = visibleComposites,
                    editingChildBlockId = editingChildBlockId,
                    onPropertyChange = { id, name, value -> blockState.updateBlockProperty(id, name, value) },
                    onNavigateToVariable = {
                        hoveredVariableName = it
                        viewState.showDialog(EditorViewState.DialogType.VARIABLES)
                    },
                    onNavigateToResource = { type, name ->
                        navigateToResource(type, name, blockState, viewState)
                    },
                    onRename = { id, name ->
                        // Handle rename logic for composites
                        val composite = visibleComposites.find { it.id == id }
                        composite?.name = name
                    },
                    onEditChild = { childId ->
                        viewState.enterComposite(viewState.selectedCompositeId!!)
                        editingChildBlockId = childId
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

// Generate default names for resources
private fun generateDefaultName(resourceType: ResourceType): String {
    val randomId = UUID.randomUUID().toString().substring(0, 4)

    val prefix = resourceType.displayName.lowercase(Locale.getDefault()).replace(" ", "-")
    return "$prefix-$randomId"
}

private fun createDefaultBlock(block: Block, blockState: BlockState) {
    val resourceType = block.resourceType
    val newBlock = block.copy(
        id = UUID.randomUUID().toString(),
        content = generateDefaultName(resourceType),
        resourceType = resourceType,
        description = block.description
    )
    blockState.addBlock(newBlock)
}

private fun navigateToResource(
    resourceType: String,
    resourceName: String,
    blockState: BlockState,
    viewState: EditorViewState
) {
    blockState.allBlocks.find { b ->
        b.resourceType.resourceName == resourceType &&
                formatResourceName(b.content) == resourceName
    }?.let { foundBlock ->
        viewState.selectBlock(foundBlock.id)
        viewState.centerOn(foundBlock.position, Offset(400f, 300f))
    }
}