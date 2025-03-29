package terraformbuilder.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import terraformbuilder.terraform.TerraformVariable
import terraformbuilder.terraform.VariableType

@Composable
fun variableDialog(
    onDismiss: () -> Unit,
    variables: List<TerraformVariable>,
    onAddVariable: (TerraformVariable) -> Unit,
    onRemoveVariable: (String) -> Unit,
    onUpdateVariable: (String, TerraformVariable) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingVariable by remember { mutableStateOf<TerraformVariable?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Terraform Variables",
                        style = MaterialTheme.typography.h6
                    )
                    Button(
                        onClick = { showAddDialog = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Variable")
                        Spacer(Modifier.width(4.dp))
                        Text("Add Variable")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Variables List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(variables) { variable ->
                        variableListItem(
                            variable = variable,
                            onEdit = { editingVariable = variable },
                            onDelete = { onRemoveVariable(variable.name) }
                        )
                    }
                }

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }

    // Add/Edit Variable Dialog
    if (showAddDialog || editingVariable != null) {
        variableEditDialog(
            variable = editingVariable,
            onDismiss = {
                showAddDialog = false
                editingVariable = null
            },
            onSave = { variable ->
                if (editingVariable != null) {
                    onUpdateVariable(editingVariable!!.name, variable)
                } else {
                    onAddVariable(variable)
                }
                showAddDialog = false
                editingVariable = null
            }
        )
    }
}

@Composable
private fun variableListItem(
    variable: TerraformVariable,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = variable.name,
                    style = MaterialTheme.typography.subtitle1
                )
                Text(
                    text = "Type: ${variable.type.displayName()}",
                    style = MaterialTheme.typography.caption
                )
                if (variable.description.isNotEmpty()) {
                    Text(
                        text = variable.description,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit Variable")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete Variable")
                }
            }
        }
    }
}

@Composable
private fun variableEditDialog(
    variable: TerraformVariable? = null,
    onDismiss: () -> Unit,
    onSave: (TerraformVariable) -> Unit
) {
    var name by remember { mutableStateOf(variable?.name ?: "") }
    var type by remember { mutableStateOf(variable?.type ?: VariableType.STRING) }
    var description by remember { mutableStateOf(variable?.description ?: "") }
    var defaultValue by remember { mutableStateOf(variable?.defaultValue ?: "") }
    var sensitive by remember { mutableStateOf(variable?.sensitive ?: false) }
    var nameError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .width(400.dp)
            ) {
                Text(
                    text = if (variable == null) "Add Variable" else "Edit Variable",
                    style = MaterialTheme.typography.h6
                )

                Spacer(Modifier.height(16.dp))

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Name") },
                    isError = nameError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError != null) {
                    Text(
                        text = nameError!!,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Type dropdown
                var expanded by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = type.displayName(),
                    onValueChange = { },
                    label = { Text("Type") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.KeyboardArrowDown, "Select Type")
                        }
                    }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    VariableType.entries.forEach { variableType ->
                        DropdownMenuItem(
                            onClick = {
                                type = variableType
                                expanded = false
                            }
                        ) {
                            Text(variableType.displayName())
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Default value field
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text("Default Value") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Sensitive checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = sensitive,
                        onCheckedChange = { sensitive = it }
                    )
                    Text("Sensitive")
                }

                Spacer(Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = "Name is required"
                                return@Button
                            }
                            onSave(
                                TerraformVariable(
                                    name = name,
                                    type = type,
                                    description = description,
                                    defaultValue = defaultValue.takeIf { it.isNotEmpty() },
                                    sensitive = sensitive
                                )
                            )
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
} 