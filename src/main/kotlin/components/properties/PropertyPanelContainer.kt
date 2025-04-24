package terraformbuilder.components.properties

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import terraformbuilder.components.EditorViewState
import terraformbuilder.components.block.Block
import terraformbuilder.components.block.BlockState
import terraformbuilder.components.block.CompositeBlock

@Composable
fun propertyPanelContainer(
    viewState: EditorViewState,
    blockState: BlockState,
    visibleBlocks: List<Block>,
    visibleComposites: List<CompositeBlock>,
    editingChildBlockId: String?,
    onPropertyChange: (String, String, String) -> Unit,
    onNavigateToVariable: (String) -> Unit,
    onNavigateToResource: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onEditChild: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        // Editing a composite block
        viewState.selectedCompositeId != null -> {
            val composite = visibleComposites.find { it.id == viewState.selectedCompositeId }
            composite?.let {
                compositePropertyEditorPanel(
                    compositeBlock = it,
                    onPropertyChange = { name, value -> onPropertyChange(it.id, name, value) },
                    onRename = { newName -> onRename(it.id, newName) },
                    onEditChild = onEditChild,
                    modifier = modifier
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
                        onPropertyChange(it.id, propertyName, propertyValue)
                    },
                    onNavigateToVariable = onNavigateToVariable,
                    onNavigateToResource = onNavigateToResource,
                    modifier = modifier
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
                        onPropertyChange(it.id, propertyName, propertyValue)
                    },
                    onNavigateToVariable = onNavigateToVariable,
                    onNavigateToResource = onNavigateToResource,
                    modifier = modifier
                )
            }
        }
    }
}