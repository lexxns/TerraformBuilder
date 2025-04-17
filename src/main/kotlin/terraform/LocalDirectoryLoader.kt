package terraformbuilder.terraform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Service for loading Terraform files from a local directory
 */
class LocalDirectoryLoader {
    
    /**
     * Loads all Terraform (.tf) files from a local directory
     * 
     * @param directory The directory containing Terraform files
     * @return List of file contents as strings
     */
    suspend fun loadTerraformFiles(directory: File): List<String> = withContext(Dispatchers.IO) {
        try {
            // Find all .tf files in the directory
            val terraformFiles = directory.listFiles { file ->
                file.isFile && file.name.endsWith(".tf")
            } ?: return@withContext emptyList()
            
            println("LOCALDIR: Found ${terraformFiles.size} Terraform files: ${terraformFiles.map { it.name }}")
            
            val contents = mutableListOf<String>()
            
            // Load content from each file
            terraformFiles.forEach { file ->
                try {
                    val content = file.readText()
                    println("LOCALDIR: Successfully loaded ${file.name} (${content.length} characters)")
                    println("LOCALDIR: Content preview for ${file.name}:")
                    println(content.take(500))
                    println("-------------------")
                    
                    contents.add(content)
                } catch (e: Exception) {
                    println("LOCALDIR: Failed to load ${file.name}: ${e.message}")
                }
            }
            
            if (contents.isEmpty()) {
                println("LOCALDIR: Warning - No terraform files were loaded!")
            }
            
            return@withContext contents
        } catch (e: Exception) {
            println("LOCALDIR: Error loading files from directory: ${e.message}")
            e.printStackTrace()
            return@withContext emptyList()
        }
    }
}