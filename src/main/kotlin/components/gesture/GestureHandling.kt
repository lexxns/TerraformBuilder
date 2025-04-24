package terraformbuilder.components.gesture

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import terraformbuilder.components.EditorViewState
import terraformbuilder.components.block.Block
import terraformbuilder.components.block.BlockState
import terraformbuilder.components.block.CompositeBlock
import terraformbuilder.components.block.ConnectionPointType

@Composable
fun editorGestureHandler(
    viewState: EditorViewState,
    blockState: BlockState,
    visibleBlocks: List<Block>,
    visibleComposites: List<CompositeBlock>,
    density: Float
): Modifier {
    return Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset ->
                // Handle drag start logic
                val workspacePoint = viewState.screenToWorkspace(offset, density)

                handleDragStart(
                    viewState = viewState,
                    workspacePoint = workspacePoint,
                    offset = offset,
                    visibleComposites = visibleComposites,
                    visibleBlocks = visibleBlocks
                )
            },
            onDrag = { change, dragAmount ->
                change.consume()

                handleDrag(
                    viewState = viewState,
                    dragAmount = dragAmount,
                    density = density,
                    blockState = blockState,
                    visibleBlocks = visibleBlocks,
                    visibleComposites = visibleComposites
                )
            },
            onDragEnd = {
                handleDragEnd(
                    viewState = viewState,
                    visibleComposites = visibleComposites
                )
            },
            onDragCancel = {
                viewState.cancelDrag()
            }
        )
    }
}

private fun handleDragStart(
    viewState: EditorViewState,
    workspacePoint: Offset,
    offset: Offset,
    visibleComposites: List<CompositeBlock>,
    visibleBlocks: List<Block>
) {
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
}

private fun handleDrag(
    viewState: EditorViewState,
    dragAmount: Offset,
    density: Float,
    blockState: BlockState,
    visibleBlocks: List<Block>,
    visibleComposites: List<CompositeBlock>
) {
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

        // Update drag state
        viewState.updateDrag(viewState.dragStartPosition!! + dragAmount, dragAmount)
    }
}

private fun handleDragEnd(
    viewState: EditorViewState,
    visibleComposites: List<CompositeBlock>
) {
    // Keep selection state
    if (viewState.dragStartBlockId != null) {
        // Check if it's a composite or regular block
        if (visibleComposites.any { it.id == viewState.dragStartBlockId }) {
            viewState.selectComposite(viewState.dragStartBlockId)
        } else {
            viewState.selectBlock(viewState.dragStartBlockId)
        }
    }

    // End drag operation
    viewState.endDrag()
}

// Helper extension function for converting between Offset and dp
private fun Offset.toDpOffset(density: Float): Offset {
    return Offset(x / density, y / density)
}

fun handleConnectionDragStart(
    block: Block,
    pointType: ConnectionPointType,
    blockState: BlockState,
    viewState: EditorViewState
) {
    blockState.startConnectionDrag(block, pointType)
    val connectionPoint = block.getConnectionPointPosition(pointType)
    blockState.updateDragPosition(connectionPoint)
    viewState.startMouseDown()
}