package terraformbuilder.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@Composable
fun launcherScreen(
    projectState: ProjectState,
    onCreateNewProject: (String, String) -> Unit,
    onOpenProject: (Project) -> Unit,
    onRemoveFromRecent: (Project) -> Unit
) {
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var projectDescription by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Terraform Builder",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Main content
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left panel - Recent Projects
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Projects",
                            style = MaterialTheme.typography.h6
                        )
                        IconButton(onClick = { showNewProjectDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Project"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (projectState.recentProjects.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recent projects",
                                style = MaterialTheme.typography.body1,
                                color = Color.Gray
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(projectState.recentProjects) { project ->
                                RecentProjectItem(
                                    project = project,
                                    onClick = { onOpenProject(project) },
                                    onRemove = { onRemoveFromRecent(project) }
                                )
                            }
                        }
                    }
                }
            }

            // Right panel - Welcome message
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colors.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Welcome to Terraform Builder",
                        style = MaterialTheme.typography.h5
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Create or open a project to get started",
                        style = MaterialTheme.typography.body1,
                        color = Color.Gray
                    )
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
                            onCreateNewProject(projectName, projectDescription)
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

@Composable
private fun RecentProjectItem(
    project: Project,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.subtitle1
                )
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
                Text(
                    text = "Last opened: ${project.lastOpened.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"))}",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from recent",
                    tint = Color.Gray
                )
            }
        }
    }
} 