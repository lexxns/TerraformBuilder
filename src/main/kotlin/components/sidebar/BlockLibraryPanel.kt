package terraformbuilder.components.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import terraformbuilder.ResourceType
import terraformbuilder.components.block.Block
import terraformbuilder.components.block.BlockType
import terraformbuilder.components.block.createBlock
import terraformbuilder.terraform.ResourceTypeCategorizer
import terraformbuilder.terraform.TerraformProperties
import java.util.*

@Composable
fun blockLibraryPanel(
    modifier: Modifier = Modifier,
    onBlockSelected: (Block) -> Unit,
    onGithubClick: () -> Unit,
    onLocalDirectoryClick: () -> Unit,
    onVariablesClick: () -> Unit,
    onGenerateTerraformClick: () -> Unit,
    highlightVariableButton: Boolean = false,
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
                onClick = onLocalDirectoryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("Load from Directory")
            }

            Button(
                onClick = onVariablesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = if (highlightVariableButton) {
                    // Use a glowing highlight effect for the variables button
                    ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colors.onPrimary
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    text = "Manage Variables",
                    style = if (highlightVariableButton) {
                        MaterialTheme.typography.button.copy(
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        MaterialTheme.typography.button
                    }
                )
            }

            Button(
                onClick = onGenerateTerraformClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primaryVariant,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text(
                    text = "Generate Terraform",
                    style = MaterialTheme.typography.button.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
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