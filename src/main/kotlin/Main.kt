package terraformbuilder

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import terraformbuilder.components.BlockState
import terraformbuilder.components.editor
import terraformbuilder.project.Project
import terraformbuilder.project.ProjectManager
import terraformbuilder.project.launcherScreen
import terraformbuilder.terraform.VariableState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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

                println("Loaded ${blocks.size} blocks and ${variables.size} variables")
            } catch (e: Exception) {
                println("Error loading project: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showOpenProjectDialog by remember { mutableStateOf(false) }
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

                println("Loaded ${blocks.size} blocks and ${variables.size} variables")
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
    val onOpenProjectFromMenu = { onOpenProject(projectState.value.currentProject!!) }
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
            onRemoveFromRecent = onRemoveFromRecent
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
}


fun main() = application {
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