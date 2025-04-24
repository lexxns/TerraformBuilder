package terraformbuilder.terraform

/**
 * Constants used throughout the Terraform processing pipeline
 */
object TerraformConstants {
    // Markers for Terraform string interpolation to prevent conflicts with Kotlin's interpolation
    const val INTERP_START = "____INTERP_S____"
    const val INTERP_END = "____INTERP_E____"

    // Regex pattern to find Terraform interpolation, accounting for nested braces
    private val INTERPOLATION_PATTERN = """\$\{([^{}]*(\{[^{}]*}[^{}]*)*)}""".toRegex()

    // Regex to match our custom interpolation markers
    // Need to escape the marker strings since they contain special characters
    private val MARKER_PATTERN = """____INTERP_S____(.*?)____INTERP_E____""".toRegex()

    /**
     * Replace Terraform interpolation syntax ${...} with our custom markers
     */
    fun preprocessInterpolation(content: String): String {
        return content.replace(INTERPOLATION_PATTERN) { matchResult ->
            val interpolationContent = matchResult.groupValues[1]
            "$INTERP_START$interpolationContent$INTERP_END"
        }
    }

    /**
     * Restore Terraform interpolation syntax from our custom markers
     */
    fun restoreInterpolation(content: String): String {
        return content
            .replace(INTERP_START, "\${")
            .replace(INTERP_END, "}")
    }

    /**
     * Check if a string contains interpolation markers
     */
    fun containsInterpolation(content: String): Boolean {
        return content.contains(INTERP_START) && content.contains(INTERP_END)
    }

    /**
     * Extract all interpolation expressions from a string with our markers
     * Returns a list of the contents inside the interpolation expressions
     */
    fun extractInterpolations(content: String): List<String> {
        val matches = MARKER_PATTERN.findAll(content)
        return matches.map { it.groupValues[1] }.toList()
    }
}

// Format resource name for Terraform compatibility
// Should match the format used in TerraformCodeGenerator.formatResourceName
fun formatResourceName(content: String): String {
    // Convert to valid Terraform resource name: lowercase, underscores, no special chars
    return content.lowercase().replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
}