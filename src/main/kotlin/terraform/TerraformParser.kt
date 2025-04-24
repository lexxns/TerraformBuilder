package terraformbuilder.terraform

import com.bertramlabs.plugins.hcl4j.HCLParser
import org.json.JSONObject
import terraformbuilder.ResourceType
import terraformbuilder.components.block.Block
import terraformbuilder.components.block.BlockType
import java.io.StringReader

data class TerraformResource(
    val type: String,
    val name: String,
    val properties: Map<String, Any?>
)

class TerraformParser {
    data class ParseResult(
        val resources: List<TerraformResource>,
        val variables: List<TerraformVariable>
    )

    private val categorizer = ResourceTypeCategorizer()

    /**
     * Preprocesses HCL content to handle string interpolation properly
     * Replaces Terraform interpolation syntax ${...} with custom markers to prevent
     * the parser from misinterpreting them
     */
    private fun preprocessHCL(content: String): String {
        // Use the constant method to preprocess the content
        return TerraformConstants.preprocessInterpolation(content)
    }

    /**
     * Safely converts an HCL value to a string that preserves interpolation markers
     * We keep the markers in the internal representation and only restore them
     * when generating the final output
     */
    private fun preserveInterpolationString(value: Any?): String {
        if (value == null) return ""

        // For direct strings, just return the value with markers intact
        if (value is String) return value

        // For maps, we need special handling to preserve the structure
        if (value is Map<*, *>) {
            return when {
                // For policy documents, convert to proper JSON
                value.keys.any { key ->
                    key.toString().contains("policy", ignoreCase = true) ||
                            key.toString().contains("document", ignoreCase = true)
                } -> {
                    try {
                        // Convert to JSON but keep our interpolation markers
                        JSONObject(value).toString(2)
                    } catch (e: Exception) {
                        // Fall back to simple string representation
                        "{${
                            value.entries.joinToString(", ") {
                                "${it.key} = ${preserveInterpolationString(it.value)}"
                            }
                        }}"
                    }
                }

                // For environment variables, preserve the structure
                value.keys.any { key -> key.toString() == "variables" } -> {
                    "{${
                        value.entries.joinToString(", ") {
                            "${it.key} = ${preserveInterpolationString(it.value)}"
                        }
                    }}"
                }

                // Default map handling
                else -> {
                    "{${
                        value.entries.joinToString(", ") {
                            "${it.key} = ${preserveInterpolationString(it.value)}"
                        }
                    }}"
                }
            }
        }

        // For lists, preserve each element
        if (value is List<*>) {
            return "[${value.joinToString(", ") { preserveInterpolationString(it) }}]"
        }

        // Default case - use toString
        return value.toString()
    }

    fun parse(content: String): ParseResult {
        try {
            val parser = HCLParser()

            // Preprocess to replace ${...} with our custom markers
            val preprocessedContent = preprocessHCL(content)
            println("PARSER: Preprocessed Terraform content")

            // Parse the preprocessed content
            val result = parser.parse(StringReader(preprocessedContent))
            println("PARSER: Successfully parsed HCL content")

            // Debug print the raw structure
            // debugPrintStructure(result)

            return ParseResult(
                resources = parseResources(result),
                variables = parseVariables(result)
            )
        } catch (e: Exception) {
            println("PARSER: Error parsing HCL content: ${e.message}")
            e.printStackTrace()
            return ParseResult(emptyList(), emptyList())
        }
    }

    private fun parseResources(hclContent: Map<*, *>): List<TerraformResource> {
        val resources = mutableListOf<TerraformResource>()

        hclContent.forEach { (key, value) ->
            when (key) {
                "resource" -> parseResourceBlocks(value, resources)
                "module" -> parseModuleBlocks(value, resources)
                "data" -> println("PARSER: Found data block (not processing)")
            }
        }
        return resources
    }

    private fun parseResourceBlocks(value: Any?, resources: MutableList<TerraformResource>) {
        when (value) {
            is Map<*, *> -> {
                value.forEach { (type, instances) ->
                    parseResourceInstances(type.toString(), instances, resources)
                }
            }

            is List<*> -> {
                value.forEach { item ->
                    if (item is Map<*, *>) {
                        item.forEach { (type, instances) ->
                            parseResourceInstances(type.toString(), instances, resources)
                        }
                    }
                }
            }
        }
    }

    private fun parseModuleBlocks(value: Any?, resources: MutableList<TerraformResource>) {
        when (value) {
            is Map<*, *> -> {
                value.forEach { (name, config) ->
                    if (config is Map<*, *>) {
                        // Convert properties with proper interpolation preservation
                        val properties = config.entries.associate { (propKey, propValue) ->
                            propKey.toString() to propValue
                        }

                        resources.add(
                            TerraformResource(
                                type = "module",
                                name = name.toString(),
                                properties = properties
                            )
                        )
                    }
                }
            }

            is List<*> -> {
                value.forEach { item ->
                    if (item is Map<*, *>) {
                        item.forEach { (name, config) ->
                            if (config is Map<*, *>) {
                                // Convert properties with proper interpolation preservation
                                val properties = config.entries.associate { (propKey, propValue) ->
                                    propKey.toString() to propValue
                                }

                                resources.add(
                                    TerraformResource(
                                        type = "module",
                                        name = name.toString(),
                                        properties = properties
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseResourceInstances(type: String, instances: Any?, resources: MutableList<TerraformResource>) {
        if (instances is Map<*, *>) {
            instances.forEach { (name, props) ->
                if (props is Map<*, *>) {
                    // Keep properties as raw as possible to preserve interpolation
                    val properties = props.entries.associate { (propKey, propValue) ->
                        propKey.toString() to propValue
                    }

                    resources.add(
                        TerraformResource(
                            type = type,
                            name = name.toString(),
                            properties = properties
                        )
                    )
                }
            }
        }
    }

    private fun parseVariables(hclContent: Map<*, *>): List<TerraformVariable> {
        val variables = mutableListOf<TerraformVariable>()

        hclContent["variable"]?.let { variableBlocks ->
            when (variableBlocks) {
                is Map<*, *> -> {
                    variableBlocks.forEach { (name, config) ->
                        if (config is Map<*, *>) {
                            variables.add(createVariableFromConfig(name.toString(), config))
                        }
                    }
                }

                is List<*> -> {
                    variableBlocks.forEach { block ->
                        if (block is Map<*, *>) {
                            block.forEach { (name, config) ->
                                if (config is Map<*, *>) {
                                    variables.add(createVariableFromConfig(name.toString(), config))
                                }
                            }
                        }
                    }
                }
            }
        }

        return variables
    }

    fun convertToBlocks(resources: List<TerraformResource>): List<Block> {
        return resources.map { resource ->
            // Convert properties to string values for our Block class
            // IMPORTANT: Preserve interpolation syntax in strings
            val stringProperties = resource.properties.mapValues { (_, value) ->
                preserveInterpolationString(value)
            }

            Block(
                id = "${resource.type}_${resource.name}",
                type = determineBlockType(ResourceType.fromResourceName(resource.type)),
                content = when (resource.type) {
                    "module" -> "Module: ${resource.name}"
                    else -> "${ResourceType.fromResourceName(resource.type).displayName}: ${resource.name}"
                },
                resourceType = ResourceType.fromResourceName(resource.type),
                description = TerraformProperties.getResourceDescription(ResourceType.fromResourceName(resource.type)),
                properties = stringProperties.toMutableMap()
            )
        }
    }

    private fun determineBlockType(resourceType: ResourceType): BlockType {
        return categorizer.determineBlockType(resourceType)
    }

    private fun createVariableFromConfig(name: String, config: Map<*, *>): TerraformVariable {
        println("PARSER: Creating variable '$name' with config: $config")

        // Parse type
        val typeStr = (config["type"] as? String)?.uppercase() ?: "STRING"
        val type = try {
            VariableType.valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            println("PARSER: Unknown type '$typeStr' for variable '$name', defaulting to STRING")
            VariableType.STRING
        }

        // Parse description
        val description = (config["description"] as? String) ?: ""

        // Parse default value
        val defaultValue = config["default"]?.toString()

        // Parse sensitive flag
        val sensitive = (config["sensitive"] as? Boolean) ?: false

        return TerraformVariable(
            name = name,
            type = type,
            description = description,
            defaultValue = defaultValue,
            sensitive = sensitive
        ).also {
            println("PARSER: Created variable: $it")
        }
    }
}