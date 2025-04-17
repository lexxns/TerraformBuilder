package terraformbuilder

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import terraformbuilder.components.BlockState
import terraformbuilder.components.editor
import terraformbuilder.components.loadDirectoryDialog
import terraformbuilder.project.Project
import terraformbuilder.project.ProjectManager
import terraformbuilder.project.launcherScreen
import terraformbuilder.terraform.LocalDirectoryLoader
import terraformbuilder.terraform.TerraformParser
import terraformbuilder.terraform.TerraformProperties
import terraformbuilder.terraform.VariableState

@Composable
@Preview
fun app() {
    val blockState = remember { BlockState() }
    val projectState = remember { mutableStateOf(ProjectManager.loadProjectState()) }
    val variableState = remember { VariableState() }

    // Load full project data if there's a current project
    LaunchedEffect(projectState.value.currentProject) {
        projectState.value.currentProject?.let { project ->
            try {
                val (loadedProject, blocks, variables) = ProjectManager.loadProject(project.id)
                    ?: throw Exception("Failed to load project")

                // Update project state
                projectState.value = projectState.value.setCurrentProject(loadedProject)

                // Load blocks and variables
                blockState.clearAll()
                blocks.forEach { block ->
                    blockState.addBlock(block)
                }

                // Restore connections
                val connections = ProjectManager.loadConnections(project.id)
                if (connections.isNotEmpty()) {
                    blockState.restoreConnections(connections)
                }

                variableState.clearAll()
                variables.forEach { variableState.addVariable(it) }
            } catch (e: Exception) {
                println("Error loading project: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showOpenProjectDialog by remember { mutableStateOf(false) }
    var showLocalDirectoryDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDescription by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Project management functions
    val onCreateNewProject: (String, String) -> Unit = { name, description ->
        val project = ProjectManager.createProject(
            name = name,
            description = description
        )
        projectState.value = projectState.value.setCurrentProject(project)
        ProjectManager.saveProjectState(projectState.value)
        // Clear existing state for new project
        blockState.clearAll()
        variableState.clearAll()
    }

    val onOpenProject: (Project) -> Unit = { project ->
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val (loadedProject, blocks, variables) = ProjectManager.loadProject(project.id)
                    ?: throw Exception("Failed to load project")

                // Update project state
                projectState.value = projectState.value.setCurrentProject(loadedProject)

                // Load blocks and variables
                blockState.clearAll()
                blocks.forEach { block ->
                    blockState.addBlock(block)
                }

                // Restore connections
                val connections = ProjectManager.loadConnections(project.id)
                if (connections.isNotEmpty()) {
                    blockState.restoreConnections(connections)
                }

                variableState.clearAll()
                variables.forEach { variableState.addVariable(it) }
            } catch (e: Exception) {
                errorMessage = "Error loading project: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val onSaveProject: () -> Unit = {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                projectState.value.currentProject?.let { project ->
                    ProjectManager.saveProject(
                        project = project,
                        blocks = blockState.blocks,
                        variables = variableState.variables,
                        connections = blockState.connections
                    )
                    ProjectManager.saveProjectState(projectState.value)
                }
            } catch (e: Exception) {
                errorMessage = "Error saving project: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val onSaveProjectAs: () -> Unit = {
        // Implementation of onSaveProjectAs
    }

    val onCloseProject: () -> Unit = {
        projectState.value = projectState.value.setCurrentProject(null)
        blockState.clearAll()
        variableState.clearAll()
        ProjectManager.saveProjectState(projectState.value)
    }

    val onExit: () -> Unit = { kotlin.system.exitProcess(0) }

    // Callbacks for menu actions
    val onNewProjectFromMenu = { name: String, description: String ->
        onCreateNewProject(name, description)
    }
    val onOpenProjectFromMenu = { showOpenProjectDialog = true }
    val onSaveProjectFromMenu = { onSaveProject() }
    val onSaveProjectAsFromMenu = { onSaveProjectAs() }
    val onCloseProjectFromMenu = { onCloseProject() }
    val onExitFromMenu = { onExit() }

    // Callback for removing projects from recent list
    val onRemoveFromRecent = { project: Project ->
        projectState.value = projectState.value.removeRecentProject(project.id)
        ProjectManager.saveProjectState(projectState.value)
    }

    // Show launcher if no project is open
    if (projectState.value.currentProject == null) {
        launcherScreen(
            projectState = projectState.value,
            onCreateNewProject = onCreateNewProject,
            onOpenProject = onOpenProject,
            onRemoveFromRecent = onRemoveFromRecent,
            onLoadFromDirectory = { showLocalDirectoryDialog = true }
        )
    } else {
        editor(
            onNewProject = onNewProjectFromMenu,
            onOpenProject = onOpenProjectFromMenu,
            onSaveProject = onSaveProjectFromMenu,
            onSaveProjectAs = onSaveProjectAsFromMenu,
            onCloseProject = onCloseProjectFromMenu,
            onExit = onExitFromMenu,
            blockState = blockState,
            variableState = variableState
        )
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
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newProjectDescription,
                        onValueChange = { newProjectDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            onCreateNewProject(newProjectName, newProjectDescription)
                            showNewProjectDialog = false
                            newProjectName = ""
                            newProjectDescription = ""
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

    // Open Project Dialog
    if (showOpenProjectDialog) {
        AlertDialog(
            onDismissRequest = { showOpenProjectDialog = false },
            title = { Text("Open Project") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (projectState.value.recentProjects.isEmpty()) {
                        Text(
                            text = "No recent projects",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        projectState.value.recentProjects.forEach { project ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenProject(project)
                                        showOpenProjectDialog = false
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.subtitle1
                                    )
                                    Text(
                                        text = project.description,
                                        style = MaterialTheme.typography.body2,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { onRemoveFromRecent(project) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove from recent"
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOpenProjectDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Local Directory Dialog
    if (showLocalDirectoryDialog) {
        loadDirectoryDialog(
            onDismiss = {
                showLocalDirectoryDialog = false
                errorMessage = null
            },
            onSelectDirectory = { directory ->
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        println("APP: Processing local directory: ${directory.absolutePath}")
                        
                        // Create a new project first
                        val projectName = directory.name
                        val projectDescription = "Imported from ${directory.absolutePath}"
                        val project = ProjectManager.createProject(projectName, projectDescription)
                        projectState.value = projectState.value.setCurrentProject(project)
                        ProjectManager.saveProjectState(projectState.value)
                        
                        // Clear existing blocks and variables
                        blockState.clearAll()
                        variableState.clearAll()

                        val directoryLoader = LocalDirectoryLoader()
                        val files = directoryLoader.loadTerraformFiles(directory)

                        if (files.isEmpty()) {
                            errorMessage = "No Terraform files found in directory"
                            return@launch
                        }

                        println("APP: Loaded ${files.size} files from local directory")

                        val parser = TerraformParser()

                        // Parse all files
                        files.forEach { file ->
                            val parseResult = parser.parse(file)

                            // Add variables
                            parseResult.variables.forEach { variable ->
                                println("APP: Adding variable ${variable.name}")
                                variableState.addVariable(variable)
                            }

                            // Convert and add resources
                            val blocks = parser.convertToBlocks(parseResult.resources)
                            blocks.forEachIndexed { index, block ->
                                val row = index / 3
                                val col = index % 3
                                val position = Offset(
                                    50f + col * 200f,
                                    50f + row * 150f
                                )
                                println("APP: Adding block ${block.content} at position $position")
                                blockState.addBlock(block.copy(_position = position))
                            }
                        }

                        if (blockState.blocks.isEmpty() && variableState.variables.isEmpty()) {
                            errorMessage = "No Terraform resources or variables found in files"
                            return@launch
                        }
                        
                        // Save the project with the imported resources
                        ProjectManager.saveProject(
                            project = project,
                            blocks = blockState.blocks,
                            variables = variableState.variables,
                            connections = blockState.connections
                        )

                        showLocalDirectoryDialog = false
                    } catch (e: Exception) {
                        println("APP: Error loading Terraform: ${e.message}")
                        e.printStackTrace()
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }
}


fun main() = application {
    // Initialize schema at startup
    TerraformProperties.initialize()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Terraform Builder",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            position = WindowPosition(100.dp, 100.dp)
        )
    ) {
        MaterialTheme {
            app()
        }
    }
}