package terraformbuilder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun compositePropertyEditorPanel(
    compositeBlock: CompositeBlock,
    onPropertyChange: (String, String) -> Unit,
    onRename: (String) -> Unit,
    onEditChild: (String) -> Unit, // When user wants to edit a specific child
    onNavigateToVariable: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val icon = remember(compositeBlock.iconCode) {
        try {
            // Try to find the icon in Icons.Filled
            val iconClass = Icons.Filled::class.java
            val field = iconClass.getDeclaredField(compositeBlock.iconCode)
            field.isAccessible = true
            field.get(Icons.Filled) as ImageVector
        } catch (e: Exception) {
            // Fallback to a default icon
            Icons.Default.Category
        }
    }

    Card(
        modifier = modifier
            .widthIn(min = 320.dp, max = 400.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with composite type icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = compositeBlock.name,
                    tint = Color.White
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Editable name field
                var editingName by remember { mutableStateOf(false) }
                var nameText by remember { mutableStateOf(compositeBlock.name) }

                if (editingName) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        singleLine = true,
                        keyboardActions = KeyboardActions(
                            onDone = {
                                editingName = false
                                onRename(nameText)
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = compositeBlock.name,
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { editingName = true }
                    )
                }
            }

            // Composite type
            Text(
                text = compositeBlock.name,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )

            // Description
            Text(
                text = compositeBlock.description,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Properties section
            Text(
                text = "Properties",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Children section
            Text(
                text = "Resources (${compositeBlock.children.size})",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )

            // List of child blocks
            compositeBlock.children.forEach { childBlock ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onEditChild(childBlock.id) },
                    elevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color indicator based on block type
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    color = BlockTypeColors.getColor(childBlock.type),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = childBlock.content,
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = childBlock.resourceType.displayName,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        IconButton(
                            onClick = { onEditChild(childBlock.id) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit resource",
                                tint = MaterialTheme.colors.primary
                            )
                        }
                    }
                }
            }
        }
    }
}