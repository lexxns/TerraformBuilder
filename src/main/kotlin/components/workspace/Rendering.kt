package terraformbuilder.components.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import terraformbuilder.components.EditorViewState
import terraformbuilder.components.block.*
import terraformbuilder.components.fileMenu

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val gridSize = 50f
        val gridColor = Color(0xFFE0E0E0)

        // Draw horizontal grid lines
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

        // Draw vertical grid lines
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
}

@Composable
fun RenderBlocks(
    blocks: List<Block>,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit,
    onBlockSelected: (String) -> Unit,
    onUpdateBlockPosition: (String, Offset) -> Unit,
    onUpdateBlockContent: (String, String) -> Unit,
    onUpdateBlockSize: (String, Offset) -> Unit,
    blockState: BlockState
) {
    blocks.forEach { block ->
        blockView(
            block = block,
            onDragEnd = { newPosition ->
                onUpdateBlockPosition(block.id, newPosition)
            },
            onDragStart = { /* Handle drag start if needed */ },
            onRename = { newContent ->
                onUpdateBlockContent(block.id, newContent)
            },
            onConnectionDragStart = onConnectionDragStart,
            onUpdateBlockSize = { blockId, size ->
                onUpdateBlockSize(blockId, size)
            },
            onBlockSelected = onBlockSelected,
            isHovered = false,
            isReferenced = blockState.isBlockReferenced(block.id),
            activeDragState = if (blockState.dragState.isActive) blockState.dragState else null
        )
    }
}

@Composable
fun RenderComposites(
    composites: List<CompositeBlock>,
    onCompositeSelected: (String) -> Unit,
    viewState: EditorViewState
) {
    composites.forEach { composite ->
        compositeBlockView(
            compositeBlock = composite,
            onDragStart = { /* Handle drag start if needed */ },
            onDragEnd = { newPosition ->
                // Update composite position
                composite.position = newPosition
            },
            onRename = { newName ->
                // Update composite name
                composite.name = newName
            },
            onBlockSelected = onCompositeSelected,
            isSelected = viewState.selectedCompositeId == composite.id
        )
    }
}

@Composable
fun RenderCompositeChildren(
    compositeId: String?,
    blockState: BlockState,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit,
    onBlockSelected: (String) -> Unit,
    onUpdateBlockPosition: (String, Offset) -> Unit,
    onUpdateBlockContent: (String, String) -> Unit,
    onUpdateBlockSize: (String, Offset) -> Unit
) {
    val composite = compositeId?.let { id ->
        blockState.allComposites.find { it.id == id }
    }

    composite?.children?.forEach { childBlock ->
        blockView(
            block = childBlock,
            onDragEnd = { newPosition ->
                onUpdateBlockPosition(childBlock.id, newPosition)
            },
            onDragStart = { /* Handle drag start if needed */ },
            onRename = { newContent ->
                onUpdateBlockContent(childBlock.id, newContent)
            },
            onConnectionDragStart = onConnectionDragStart,
            onUpdateBlockSize = { blockId, size ->
                onUpdateBlockSize(blockId, size)
            },
            onBlockSelected = onBlockSelected,
            isHovered = false,
            isReferenced = false,
            activeDragState = if (blockState.dragState.isActive) blockState.dragState else null
        )
    }
}

@Composable
fun selectionActionButtons(
    viewState: EditorViewState,
    onShowGroupDialog: () -> Unit,
    onUngroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Group button for multiple selected blocks
    if (viewState.selectedBlockIds.size > 1) {
        Box(
            modifier = modifier
                .padding(16.dp)
        ) {
            FloatingActionButton(
                modifier = Modifier.align(Alignment.BottomEnd),  // Here's the correct usage
                onClick = onShowGroupDialog
            ) {
                Icon(Icons.Default.Group, "Group selected blocks")
            }
        }
    }
    // Ungroup button for selected composite
    else if (viewState.selectedCompositeId != null) {
        Box(
            modifier = modifier
                .padding(16.dp)
        ) {
            FloatingActionButton(
                modifier = Modifier.align(Alignment.BottomEnd),  // Here's the correct usage
                onClick = onUngroup
            ) {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Ungroup"
                )
            }
        }
    }
}

@Composable
fun fileMenuBar(
    onNewProject: (String, String) -> Unit,
    onOpenProject: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveProjectAs: () -> Unit,
    onCloseProject: () -> Unit,
    onExit: () -> Unit
) {
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
}