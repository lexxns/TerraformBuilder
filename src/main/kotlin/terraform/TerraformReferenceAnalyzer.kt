package terraformbuilder.terraform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * A utility class for analyzing and visualizing Terraform references
 * in properties that contain our interpolation markers.
 */
class TerraformReferenceAnalyzer {
    /**
     * Reference types that can be extracted from Terraform interpolation
     */
    sealed class Reference {
        data class VariableReference(val variableName: String) : Reference()
        data class ResourceReference(
            val resourceType: String,
            val resourceName: String,
            val attribute: String? = null
        ) : Reference()

        data class FunctionCall(val functionName: String, val content: String) : Reference()
        data class Expression(val content: String) : Reference()
    }

    /**
     * Segment types for UI visualization
     */
    sealed class ExpressionSegment {
        data class Text(val content: String) : ExpressionSegment()
        data class VariableReference(
            val variableName: String,
            val originalText: String,
            val isInterpolated: Boolean = true
        ) : ExpressionSegment()

        data class ResourceReference(
            val resourceType: String,
            val resourceName: String,
            val attribute: String? = null,
            val originalText: String,
            val isInterpolated: Boolean = true
        ) : ExpressionSegment()

        data class Function(
            val functionName: String,
            val content: String,
            val originalText: String,
            val isInterpolated: Boolean = true
        ) : ExpressionSegment()

        data class Expression(
            val content: String,
            val originalText: String,
            val isInterpolated: Boolean = true
        ) : ExpressionSegment()
    }

    /**
     * Parse a property value containing our interpolation markers into segments
     * that can be used for UI visualization
     */
    fun parseExpression(input: String): List<ExpressionSegment> {
        val segments = mutableListOf<ExpressionSegment>()
        var currentIndex = 0

        // Find all occurrences of our custom markers
        // Use a literal pattern that specifically matches our markers
        val pattern = """____INTERP_S____(.*?)____INTERP_E____""".toRegex()
        val matches = pattern.findAll(input)

        for (match in matches) {
            val startIndex = match.range.first
            val endIndex = match.range.last + 1

            // Add text before this interpolation if any
            if (startIndex > currentIndex) {
                segments.add(ExpressionSegment.Text(input.substring(currentIndex, startIndex)))
            }

            // Extract the content between our markers
            val interpolationContent = match.groupValues[1]

            // Parse the content to determine what type of reference it is
            if (interpolationContent.startsWith("var.")) {
                // Variable reference
                val variableName = interpolationContent.substring(4) // Remove "var."
                segments.add(
                    ExpressionSegment.VariableReference(
                        variableName = variableName,
                        originalText = match.value,
                        isInterpolated = true
                    )
                )
            } else if (interpolationContent.contains(".")) {
                // Resource reference pattern: type.name.attribute
                val parts = interpolationContent.split(".")
                if (parts.size >= 2) {
                    val resourceType = parts[0]
                    val resourceName = parts[1]
                    val attribute = if (parts.size > 2) parts[2] else null

                    segments.add(
                        ExpressionSegment.ResourceReference(
                            resourceType = resourceType,
                            resourceName = resourceName,
                            attribute = attribute,
                            originalText = match.value,
                            isInterpolated = true
                        )
                    )
                } else {
                    // If it doesn't match our resource pattern, treat as generic expression
                    segments.add(
                        ExpressionSegment.Expression(
                            content = interpolationContent,
                            originalText = match.value,
                            isInterpolated = true
                        )
                    )
                }
            } else if (interpolationContent.contains("(") && interpolationContent.contains(")")) {
                // Function call
                val functionMatch = """^([a-z0-9_]+)\((.*)\)$""".toRegex().find(interpolationContent)
                if (functionMatch != null) {
                    val functionName = functionMatch.groupValues[1]
                    val functionArgs = functionMatch.groupValues[2]

                    segments.add(
                        ExpressionSegment.Function(
                            functionName = functionName,
                            content = functionArgs,
                            originalText = match.value,
                            isInterpolated = true
                        )
                    )
                } else {
                    // If it doesn't match our function pattern, treat as generic expression
                    segments.add(
                        ExpressionSegment.Expression(
                            content = interpolationContent,
                            originalText = match.value,
                            isInterpolated = true
                        )
                    )
                }
            } else {
                // Generic expression
                segments.add(
                    ExpressionSegment.Expression(
                        content = interpolationContent,
                        originalText = match.value,
                        isInterpolated = true
                    )
                )
            }

            currentIndex = endIndex
        }

        // Add remaining text after the last interpolation if any
        if (currentIndex < input.length) {
            segments.add(ExpressionSegment.Text(input.substring(currentIndex)))
        }

        return segments
    }

    /**
     * Find all variable references in a property value
     */
    fun findVariableReferences(input: String): List<String> {
        return extractReferences(input)
            .filterIsInstance<Reference.VariableReference>()
            .map { it.variableName }
    }

    /**
     * Find all resource references in a property value
     */
    fun findResourceReferences(input: String): List<Pair<String, String>> {
        return extractReferences(input)
            .filterIsInstance<Reference.ResourceReference>()
            .map { Pair(it.resourceType, it.resourceName) }
    }

    /**
     * Extract all references from a property value containing our interpolation markers
     */
    fun extractReferences(input: String): List<Reference> {
        val references = mutableListOf<Reference>()
        val segments = parseExpression(input)

        segments.forEach { segment ->
            when (segment) {
                is ExpressionSegment.VariableReference -> {
                    references.add(Reference.VariableReference(segment.variableName))
                }

                is ExpressionSegment.ResourceReference -> {
                    references.add(
                        Reference.ResourceReference(
                            resourceType = segment.resourceType,
                            resourceName = segment.resourceName,
                            attribute = segment.attribute
                        )
                    )
                }

                is ExpressionSegment.Function -> {
                    references.add(
                        Reference.FunctionCall(
                            functionName = segment.functionName,
                            content = segment.content
                        )
                    )

                    // Recursively extract references from function arguments
                    references.addAll(extractReferences(segment.content))
                }

                is ExpressionSegment.Expression -> {
                    references.add(Reference.Expression(segment.content))
                }

                else -> {} // Ignore plain text
            }
        }

        return references
    }

    /**
     * Create an AnnotatedString with syntax highlighting for a property value
     * containing our interpolation markers
     */
    fun highlightExpression(input: String): AnnotatedString {
        val segments = parseExpression(input)

        return buildAnnotatedString {
            segments.forEach { segment ->
                when (segment) {
                    is ExpressionSegment.Text -> {
                        append(segment.content)
                    }

                    is ExpressionSegment.VariableReference -> {
                        // Variable references are highlighted in blue
                        if (segment.isInterpolated) {
                            append("\${")
                        }

                        withStyle(SpanStyle(color = Color(0xFF2196F3))) { // Blue
                            append("var.${segment.variableName}")
                        }

                        if (segment.isInterpolated) {
                            append("}")
                        }
                    }

                    is ExpressionSegment.ResourceReference -> {
                        // Resource references are highlighted in green
                        if (segment.isInterpolated) {
                            append("\${")
                        }

                        withStyle(SpanStyle(color = Color(0xFF4CAF50))) { // Green
                            append(segment.resourceType)
                            append(".")
                            append(segment.resourceName)
                            if (segment.attribute != null) {
                                append(".")
                                append(segment.attribute)
                            }
                        }

                        if (segment.isInterpolated) {
                            append("}")
                        }
                    }

                    is ExpressionSegment.Function -> {
                        // Functions are highlighted in purple
                        if (segment.isInterpolated) {
                            append("\${")
                        }

                        withStyle(SpanStyle(color = Color(0xFF9C27B0))) { // Purple
                            append(segment.functionName)
                            append("(")
                        }

                        // Recursively highlight function arguments
                        append(highlightExpression(segment.content))

                        withStyle(SpanStyle(color = Color(0xFF9C27B0))) { // Purple
                            append(")")
                        }

                        if (segment.isInterpolated) {
                            append("}")
                        }
                    }

                    is ExpressionSegment.Expression -> {
                        // Generic expressions are highlighted in orange
                        if (segment.isInterpolated) {
                            append("\${")
                        }

                        withStyle(SpanStyle(color = Color(0xFFFF9800))) { // Orange
                            append(segment.content)
                        }

                        if (segment.isInterpolated) {
                            append("}")
                        }
                    }
                }
            }
        }
    }
}