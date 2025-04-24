package terraformbuilder.components.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import terraformbuilder.components.EditorViewState
import terraformbuilder.components.block.*

@Composable
fun editorWorkspace(
    viewState: EditorViewState,
    blockState: BlockState,
    visibleBlocks: List<Block>,
    visibleComposites: List<CompositeBlock>,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit,
    onBlockSelected: (String) -> Unit,
    onCompositeSelected: (String) -> Unit,
    onUpdateBlockPosition: (String, Offset) -> Unit,
    onUpdateBlockContent: (String, String) -> Unit,
    onUpdateBlockSize: (String, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Navigation breadcrumb for composite view
        if (viewState.isInCompositeView()) {
            compositeNavigationBreadcrumb(
                compositeId = viewState.activeCompositeId,
                composites = blockState.allComposites,
                onExitToMainView = { viewState.exitToMainView() }
            )
        }

        // Group/ungroup action buttons
        selectionActionButtons(
            viewState = viewState,
            onShowGroupDialog = { viewState.showDialog(EditorViewState.DialogType.GROUP) },
            onUngroup = { blockState.ungroupComposite(viewState.selectedCompositeId!!) }
        )

        // Main workspace with transformations
        workspaceCanvas(
            viewState = viewState,
            blockState = blockState,
            visibleBlocks = visibleBlocks,
            visibleComposites = visibleComposites,
            onConnectionDragStart = onConnectionDragStart,
            onBlockSelected = onBlockSelected,
            onCompositeSelected = onCompositeSelected,
            onUpdateBlockPosition = onUpdateBlockPosition,
            onUpdateBlockContent = onUpdateBlockContent,
            onUpdateBlockSize = onUpdateBlockSize
        )
    }
}

@Composable
fun compositeNavigationBreadcrumb(
    compositeId: String?,
    composites: List<CompositeBlock>,
    onExitToMainView: () -> Unit
) {
    val composite = compositeId?.let { id -> composites.find { it.id == id } }

    if (composite != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onExitToMainView) {
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

@Composable
fun workspaceCanvas(
    viewState: EditorViewState,
    blockState: BlockState,
    visibleBlocks: List<Block>,
    visibleComposites: List<CompositeBlock>,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit,
    onBlockSelected: (String) -> Unit,
    onCompositeSelected: (String) -> Unit,
    onUpdateBlockPosition: (String, Offset) -> Unit,
    onUpdateBlockContent: (String, String) -> Unit,
    onUpdateBlockSize: (String, Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                translationX = viewState.panOffset.x,
                translationY = viewState.panOffset.y,
                scaleX = viewState.scale,
                scaleY = viewState.scale
            )
    ) {
        // Draw grid
        GridBackground()

        // Draw connections
        connectionCanvas(
            connections = blockState.connections,
            dragState = blockState.dragState,
            blocks = visibleBlocks,
            modifier = Modifier.fillMaxSize()
        )

        // Draw blocks based on view mode
        when {
            viewState.isInMainView() -> {
                // Draw regular blocks
                RenderBlocks(
                    blocks = visibleBlocks,
                    onConnectionDragStart = onConnectionDragStart,
                    onBlockSelected = onBlockSelected,
                    onUpdateBlockPosition = onUpdateBlockPosition,
                    onUpdateBlockContent = onUpdateBlockContent,
                    onUpdateBlockSize = onUpdateBlockSize,
                    blockState = blockState
                )

                // Draw composite blocks
                RenderComposites(
                    composites = visibleComposites,
                    onCompositeSelected = onCompositeSelected,
                    viewState = viewState
                )
            }

            else -> {
                // We're inside a composite, show its children
                RenderCompositeChildren(
                    compositeId = viewState.activeCompositeId,
                    blockState = blockState,
                    onConnectionDragStart = onConnectionDragStart,
                    onBlockSelected = onBlockSelected,
                    onUpdateBlockPosition = onUpdateBlockPosition,
                    onUpdateBlockContent = onUpdateBlockContent,
                    onUpdateBlockSize = onUpdateBlockSize
                )
            }
        }
    }
}

fun calculateVisibleBlocks(
    blockState: BlockState,
    viewState: EditorViewState,
): List<Block> {
    return if (viewState.isInMainView()) {
        blockState.allBlocks
    } else {
        blockState.getCompositeChildren(viewState.activeCompositeId!!)
    }
}

fun calculateVisibleComposites(
    blockState: BlockState,
    viewState: EditorViewState
): List<CompositeBlock> {
    return if (viewState.isInMainView()) blockState.allComposites else emptyList()
}