package terraformbuilder.terraform

import com.bertramlabs.plugins.hcl4j.HCLParser
import terraformbuilder.Block
import terraformbuilder.BlockType
import terraformbuilder.ResourceType
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

    fun parse(content: String): ParseResult {
        println("PARSER: Starting to parse content (${content.length} characters)")

        try {
            val parser = HCLParser()
            val result = parser.parse(StringReader(content))

            // Debug print the raw structure first
            println("PARSER: Raw parsed structure:")
            debugPrintStructure(result)

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
            println("PARSER: Processing top-level key: $key")

            when (key) {
                "resource" -> parseResourceBlocks(value, resources)
                "module" -> parseModuleBlocks(value, resources)
                "data" -> println("PARSER: Found data block (not processing)")
            }
        }

        println("PARSER: Found ${resources.size} resources")
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
                println("PARSER: Processing resource: $type.$name")
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
        println("PARSER: Starting to parse variables")
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

        println("PARSER: Found ${variables.size} variables")
        return variables
    }

    fun convertToBlocks(resources: List<TerraformResource>): List<Block> {
        return resources.map { resource ->
            // Convert properties to string values for our Block class
            val stringProperties = resource.properties.mapValues { (_, value) ->
                when (value) {
                    is Map<*, *> -> "{${value.entries.joinToString(", ") { "${it.key} = ${it.value}" }}}"
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
                properties = stringProperties.toMutableMap()
            ).apply {
                println("PARSER: Created block: $content with properties: ${stringProperties.keys}")
            }
        }
    }

    private fun determineBlockType(resourceType: ResourceType): BlockType {
        return when (resourceType) {
            ResourceType.LAMBDA_FUNCTION -> BlockType.COMPUTE
            ResourceType.S3_BUCKET -> BlockType.DATABASE
            ResourceType.DYNAMODB_TABLE -> BlockType.DATABASE
            ResourceType.VPC -> BlockType.NETWORKING
            ResourceType.SECURITY_GROUP -> BlockType.NETWORKING
            ResourceType.IAM_ROLE, ResourceType.IAM_POLICY -> BlockType.SECURITY
            ResourceType.CLOUDWATCH_LOG_GROUP -> BlockType.MONITORING
            ResourceType.UNKNOWN -> BlockType.INTEGRATION
            else -> BlockType.INTEGRATION
        }
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