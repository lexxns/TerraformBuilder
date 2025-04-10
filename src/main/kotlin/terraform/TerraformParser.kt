package terraformbuilder.terraform

import com.bertramlabs.plugins.hcl4j.HCLParser
import org.json.JSONObject
import terraformbuilder.ResourceType
import terraformbuilder.components.Block
import terraformbuilder.components.BlockType
import java.io.StringReader

data class TerraformResource(
    val type: String,
    val name: String,
    val properties: Map<String, Any>
)

class TerraformParser {
    data class ParseResult(
        val resources: List<TerraformResource>,
        val variables: List<TerraformVariable>
    )

    private val categorizer = ResourceTypeCategorizer()

    fun parse(content: String): ParseResult {
        try {
            val parser = HCLParser()
            val result = parser.parse(StringReader(content))

            // Debug print the raw structure first
            debugPrintStructure(result)

            return ParseResult(
                resources = parseResources(result),
                variables = parseVariables(result)
            )
        } catch (e: Exception) {
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
                        @Suppress("UNCHECKED_CAST")
                        resources.add(
                            TerraformResource(
                                type = "module",
                                name = name.toString(),
                                properties = config as Map<String, Any>
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
                                @Suppress("UNCHECKED_CAST")
                                resources.add(
                                    TerraformResource(
                                        type = "module",
                                        name = name.toString(),
                                        properties = config as Map<String, Any>
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
                    @Suppress("UNCHECKED_CAST")
                    resources.add(
                        TerraformResource(
                            type = type,
                            name = name.toString(),
                            properties = props as Map<String, Any>
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
            val stringProperties = resource.properties.mapValues { (_, value) ->
                when (value) {
                    is Map<*, *> -> {
                        // For JSON-like structures, convert to proper JSON string
                        if (value.keys.any { it.toString().contains("policy") || it.toString().contains("document") }) {
                            try {
                                // Convert the map to a JSONObject and get its string representation
                                JSONObject(value).toString(2) // Use 2 spaces for indentation
                            } catch (e: Exception) {
                                // If JSON conversion fails, fall back to the original toString
                                value.toString()
                            }
                        } else {
                            "{${value.entries.joinToString(", ") { "${it.key} = ${it.value}" }}}"
                        }
                    }

                    is List<*> -> "[${value.joinToString(", ")}]"
                    else -> {
                        // Ignore compiler, this CAN be null
                        if (value == null) {
                            ""
                        } else {
                            value.toString()
                        }
                    }
                }
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

    private fun debugPrintStructure(map: Map<*, *>, indent: String = "") {
        map.forEach { (key, value) ->
            when (value) {
                is Map<*, *> -> {
                    println("$indent$key:")
                    debugPrintStructure(value, "$indent  ")
                }

                is List<*> -> {
                    println("$indent$key: [")
                    value.forEach { item ->
                        if (item is Map<*, *>) {
                            debugPrintStructure(item, "$indent  ")
                        } else {
                            println("$indent  $item")
                        }
                    }
                    println("$indent]")
                }

                else -> println("$indent$key: $value")
            }
        }
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