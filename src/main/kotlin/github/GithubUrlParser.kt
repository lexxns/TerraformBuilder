package terraformbuilder.github

data class GithubRepoInfo(
    val owner: String,
    val repo: String,
    val path: String,
    val branch: String = "master"
)

object GithubUrlParser {
    fun parse(url: String): GithubRepoInfo? {
        // Match patterns like:
        // https://github.com/owner/repo/tree/branch/path
        // https://github.com/owner/repo/blob/branch/path
        val regex = """github\.com/([^/]+)/([^/]+)(?:/(?:tree|blob)/([^/]+))?(?:/(.*))?""".toRegex()
        
        return regex.find(url)?.let { match ->
            val (owner, repo, branch, path) = match.destructured
            GithubRepoInfo(
                owner = owner,
                repo = repo,
                branch = branch.ifEmpty { "master" },
                path = path.ifEmpty { "" }
            )
        }
    }
} 