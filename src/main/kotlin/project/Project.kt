package terraformbuilder.project

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import terraformbuilder.utils.LocalDateTimeSerializer

@Serializable
data class Project(
    val id: String,
    val name: String,
    val path: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastOpened: LocalDateTime = LocalDateTime.now(),
    val description: String = ""
)

@Serializable
data class ProjectState(
    val currentProject: Project? = null,
    val recentProjects: List<Project> = emptyList(),
    val maxRecentProjects: Int = 10
) {
    fun addRecentProject(project: Project): ProjectState {
        val updatedRecent = (listOf(project) + recentProjects)
            .distinctBy { it.id }
            .take(maxRecentProjects)
        return copy(recentProjects = updatedRecent)
    }

    fun removeRecentProject(projectId: String): ProjectState {
        return copy(recentProjects = recentProjects.filter { it.id != projectId })
    }

    fun setCurrentProject(project: Project?): ProjectState {
        return copy(
            currentProject = project,
            recentProjects = if (project != null) {
                addRecentProject(project).recentProjects
            } else {
                recentProjects
            }
        )
    }
} 