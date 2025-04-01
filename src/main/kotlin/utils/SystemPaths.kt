package terraformbuilder.utils

import java.io.File
import java.lang.System.getProperty

object SystemPaths {
    private const val PROJECT_NAME = "TerraformBuilder"

    private fun getAppDataDirectory(): File {
        val os = getProperty("os.name").lowercase()
        val userHome = File(getProperty("user.home"))

        return when {
            os.contains("windows") -> {
                File(userHome, "AppData").resolve("Local").resolve(PROJECT_NAME)
            }

            os.contains("mac") -> {
                File(userHome, "Library").resolve("Application Support").resolve(PROJECT_NAME)
            }

            else -> {
                File(userHome, ".$PROJECT_NAME")
            }
        }.also { it.mkdirs() }
    }

    fun getProjectsDirectory(): File {
        return File(getAppDataDirectory(), "projects").also { it.mkdirs() }
    }
} 