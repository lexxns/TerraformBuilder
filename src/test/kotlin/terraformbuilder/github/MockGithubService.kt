package terraformbuilder.github

import java.io.File

class MockGithubService {
    // This mock service will load Terraform files from resources for testing

    fun loadTerraformFiles(repoInfo: GithubRepoInfo): List<String> {
        // If the repo info matches our specific test case, return test files
        if (repoInfo.owner == "lexxns" &&
            repoInfo.repo == "terraform-examples" &&
            repoInfo.path.contains("aws/aws_lambda_api")
        ) {
            val resourcePath = "terraform-examples/aws/aws_lambda_api"
            val resourceDir = File(javaClass.classLoader.getResource(resourcePath)?.file ?: "")

            if (!resourceDir.exists()) {
                println("Warning: Test resource directory not found: $resourcePath")
                return emptyList()
            }

            return resourceDir.listFiles()
                ?.filter { it.extension == "tf" }
                ?.map { it.readText() }
                ?: emptyList()
        }

        // Default: return empty list if not a test case
        return emptyList()
    }
} 