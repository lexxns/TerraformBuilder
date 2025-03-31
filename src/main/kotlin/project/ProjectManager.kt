package terraformbuilder.project

import kotlinx.serialization.json.Json
import terraformbuilder.components.Block
import terraformbuilder.terraform.TerraformVariable
import terraformbuilder.utils.SystemPaths
import java.io.File
import java.time.LocalDateTime
import java.util.*

object ProjectManager {
    private const val PROJECT_STATE_FILE = "project_state.json"
    private val json = Json { prettyPrint = true }
    private val projectsDir: File by lazy { SystemPaths.getProjectsDirectory() }

    init {
        // Create projects directory if it doesn't exist
        projectsDir.mkdirs()
    }

    fun saveProjectState(state: ProjectState) {
        val stateFile = File(projectsDir, PROJECT_STATE_FILE)
        println("Saving project state to: ${stateFile.absolutePath}")
        try {
            stateFile.writeText(json.encodeToString(state))
            println("Project state saved successfully")
        } catch (e: Exception) {
            println("Error saving project state: ${e.message}")
            e.printStackTrace()
        }
    }

    fun loadProjectState(): ProjectState {
        val stateFile = File(projectsDir, PROJECT_STATE_FILE)
        println("Loading project state from: ${stateFile.absolutePath}")
        return if (stateFile.exists()) {
            try {
                val state = json.decodeFromString<ProjectState>(stateFile.readText())
                println("Project state loaded successfully")
                state
            } catch (e: Exception) {
                println("Error loading project state: ${e.message}")
                e.printStackTrace()
                ProjectState()
            }
        } else {
            println("No project state file found, creating new state")
            ProjectState()
        }
    }

    fun saveProject(project: Project, blocks: List<Block>, variables: List<TerraformVariable>) {
        val projectDir = File(projectsDir, project.id)
        println("Saving project to directory: ${projectDir.absolutePath}")
        projectDir.mkdirs()

        try {
            // Save project metadata
            val metadataFile = File(projectDir, "metadata.json")
            println("Saving project metadata to: ${metadataFile.absolutePath}")
            metadataFile.writeText(json.encodeToString(project))

            // Save blocks
            val blocksFile = File(projectDir, "blocks.json")
            println("Saving ${blocks.size} blocks to: ${blocksFile.absolutePath}")
            blocksFile.writeText(json.encodeToString(blocks))

            // Save variables
            val variablesFile = File(projectDir, "variables.json")
            println("Saving ${variables.size} variables to: ${variablesFile.absolutePath}")
            variablesFile.writeText(json.encodeToString(variables))

            println("Project saved successfully")
        } catch (e: Exception) {
            println("Error saving project: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    fun loadProject(projectId: String): Triple<Project, List<Block>, List<TerraformVariable>>? {
        val projectDir = File(projectsDir, projectId)
        println("Loading project from directory: ${projectDir.absolutePath}")
        if (!projectDir.exists()) {
            println("Project directory does not exist")
            return null
        }

        try {
            val metadataFile = File(projectDir, "metadata.json")
            val blocksFile = File(projectDir, "blocks.json")
            val variablesFile = File(projectDir, "variables.json")

            println("Loading project metadata from: ${metadataFile.absolutePath}")
            val project = json.decodeFromString<Project>(metadataFile.readText())

            println("Loading blocks from: ${blocksFile.absolutePath}")
            val blocks = json.decodeFromString<List<Block>>(blocksFile.readText())
            println("Loaded ${blocks.size} blocks")

            println("Loading variables from: ${variablesFile.absolutePath}")
            val variables = json.decodeFromString<List<TerraformVariable>>(variablesFile.readText())
            println("Loaded ${variables.size} variables")

            return Triple(project, blocks, variables)
        } catch (e: Exception) {
            println("Error loading project: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    fun createProject(name: String, description: String = ""): Project {
        return Project(
            id = UUID.randomUUID().toString(),
            name = name,
            path = projectsDir.absolutePath,
            description = description,
            lastOpened = LocalDateTime.now()
        )
    }

    fun deleteProject(projectId: String) {
        val projectDir = File(projectsDir, projectId)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
    }
} 