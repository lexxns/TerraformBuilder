package terraformbuilder.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun fileMenu(
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveProjectAs: () -> Unit,
    onCloseProject: () -> Unit,
    onExit: () -> Unit,
    isProjectOpen: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var projectDescription by remember { mutableStateOf("") }

    Box {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier
                .padding(8.dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "File Menu",
                tint = MaterialTheme.colors.onPrimary
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            // New Project
            DropdownMenuItem(
                onClick = {
                    showNewProjectDialog = true
                    showMenu = false
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface
                    )
                    Text("New Project")
                }
            }

            // Open Project
            DropdownMenuItem(
                onClick = {
                    onOpenProject()
                    showMenu = false
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null
                    )
                    Text("Open Project")
                }
            }

            // Save Project
            DropdownMenuItem(
                onClick = {
                    onSaveProject()
                    showMenu = false
                },
                enabled = isProjectOpen
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null
                    )
                    Text("Save Project")
                }
            }

            // Save Project As
            DropdownMenuItem(
                onClick = {
                    onSaveProjectAs()
                    showMenu = false
                },
                enabled = isProjectOpen
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null
                    )
                    Text("Save Project As")
                }
            }

            Divider()

            // Close Project
            DropdownMenuItem(
                onClick = {
                    onCloseProject()
                    showMenu = false
                },
                enabled = isProjectOpen
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null
                    )
                    Text("Close Project")
                }
            }

            Divider()

            // Exit
            DropdownMenuItem(
                onClick = {
                    onExit()
                    showMenu = false
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null
                    )
                    Text("Exit")
                }
            }
        }
    }

    // New Project Dialog
    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Create New Project") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = projectDescription,
                        onValueChange = { projectDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (projectName.isNotBlank()) {
                            onNewProject()
                            showNewProjectDialog = false
                            projectName = ""
                            projectDescription = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
} 