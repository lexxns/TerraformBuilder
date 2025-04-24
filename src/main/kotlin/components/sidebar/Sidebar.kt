package terraformbuilder.components.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import terraformbuilder.components.block.Block
import terraformbuilder.components.block.CompositeBlock

@Composable
fun editorSidebar(
    onBlockSelected: (Block) -> Unit,
    onTemplateSelected: (String, (String, Offset) -> CompositeBlock) -> Unit,
    onGithubClick: () -> Unit,
    onLocalDirectoryClick: () -> Unit,
    onVariablesClick: () -> Unit,
    onGenerateTerraformClick: () -> Unit,
    highlightVariableButton: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.LightGray)
    ) {
        // Block library panel (top half)
        blockLibraryPanel(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxWidth()
                .padding(8.dp),
            onBlockSelected = onBlockSelected,
            onGithubClick = onGithubClick,
            onLocalDirectoryClick = onLocalDirectoryClick,
            onVariablesClick = onVariablesClick,
            highlightVariableButton = highlightVariableButton,
            onGenerateTerraformClick = onGenerateTerraformClick
        )

        Divider(color = Color.DarkGray, thickness = 1.dp)

        // Template library panel (bottom half)
        templateLibraryPanel(
            onTemplateSelected = onTemplateSelected,
            modifier = Modifier
                .weight(0.5f)
                .fillMaxWidth()
                .padding(8.dp)
        )
    }
}