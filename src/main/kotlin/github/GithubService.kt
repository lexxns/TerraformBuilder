package terraformbuilder.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

class GithubService {
    private val apiToken = System.getenv("GITHUB_TOKEN") // Optional: for higher rate limits

    private data class GithubFile(
        val name: String,
        val path: String,
        val type: String
    )

    suspend fun loadTerraformFiles(repoInfo: GithubRepoInfo): List<String> = withContext(Dispatchers.IO) {
        println("GITHUB: Loading files from ${repoInfo.owner}/${repoInfo.repo}, branch: ${repoInfo.branch}, path: ${repoInfo.path}")
        
        val files = try {
            // First get the directory contents using GitHub API
            val apiUrl = "https://api.github.com/repos/${repoInfo.owner}/${repoInfo.repo}/contents/${repoInfo.path}?ref=${repoInfo.branch}"
            println("GITHUB: Fetching directory contents from $apiUrl")
            
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            if (apiToken != null) {
                connection.setRequestProperty("Authorization", "token $apiToken")
            }
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response)
            
            when {
                json is JsonArray -> json.mapNotNull { item ->
                    val obj = item.jsonObject
                    GithubFile(
                        name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        path = obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        type = obj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    )
                }
                json is JsonObject -> listOf(
                    GithubFile(
                        name = json["name"]?.jsonPrimitive?.content ?: return@withContext emptyList(),
                        path = json["path"]?.jsonPrimitive?.content ?: return@withContext emptyList(),
                        type = json["type"]?.jsonPrimitive?.content ?: return@withContext emptyList()
                    )
                )
                else -> emptyList()
            }
        } catch (e: Exception) {
            println("GITHUB: Error fetching directory contents: ${e.message}")
            e.printStackTrace()
            return@withContext emptyList()
        }

        // Filter for .tf files and load their contents
        val terraformFiles = files.filter { it.name.endsWith(".tf") }
        println("GITHUB: Found ${terraformFiles.size} Terraform files: ${terraformFiles.map { it.name }}")

        val contents = mutableListOf<String>()
        
        terraformFiles.forEach { file ->
            try {
                val rawUrl = "https://raw.githubusercontent.com/${repoInfo.owner}/${repoInfo.repo}/${repoInfo.branch}/${file.path}"
                println("GITHUB: Loading ${file.name} from $rawUrl")
                
                val content = URL(rawUrl).readText()
                println("GITHUB: Successfully loaded ${file.name} (${content.length} characters)")
                println("GITHUB: Content preview for ${file.name}:")
                println(content.take(500))
                println("-------------------")
                
                contents.add(content)
            } catch (e: Exception) {
                println("GITHUB: Failed to load ${file.name}: ${e.message}")
            }
        }
        
        if (contents.isEmpty()) {
            println("GITHUB: Warning - No terraform files were loaded!")
        }
        
        return@withContext contents
    }
} 
