package terraformbuilder.terraform

import terraformbuilder.Block
import terraformbuilder.BlockType
import terraformbuilder.ResourceType
import com.bertramlabs.plugins.hcl4j.HCLParser
import java.io.StringReader

data class TerraformResource(
    val type: String,
    val name: String,
    val properties: Map<String, Any>
)

class TerraformParser {
    fun parse(content: String): List<TerraformResource> {
        println("PARSER: Starting to parse content (${content.length} characters)")
        
        try {
            val parser = HCLParser()
            val result = parser.parse(StringReader(content))
            
            val resources = mutableListOf<TerraformResource>()
            
            // Debug print the raw structure first
            println("PARSER: Raw parsed structure:")
            debugPrintStructure(result)
            
            result.forEach { (key, value) ->
                println("PARSER: Processing top-level key: $key")
                
                when (key) {
                    "resource", "module" -> {  // Handle both resource and module blocks
                        println("PARSER: ${key.capitalize()} value type: ${value?.javaClass}")
                        
                        when (value) {
                            is Map<*, *> -> {
                                value.forEach { (typeOrName, instances) ->
                                    println("PARSER: Found ${key}: $typeOrName")
                                    println("PARSER: Instances type: ${instances?.javaClass}")
                                    
                                    when (instances) {
                                        is Map<*, *> -> {
                                            if (key == "module") {
                                                // For modules, the first level is the module name and contains properties directly
                                                @Suppress("UNCHECKED_CAST")
                                                val properties = instances as Map<String, Any>
                                                resources.add(TerraformResource(
                                                    type = "module",
                                                    name = typeOrName.toString(),
                                                    properties = properties
                                                ))
                                            } else {
                                                // For resources, handle as before
                                                instances.forEach { (name, props) ->
                                                    println("PARSER: Processing resource: $typeOrName.$name")
                                                    if (props is Map<*, *>) {
                                                        @Suppress("UNCHECKED_CAST")
                                                        val properties = props as Map<String, Any>
                                                        resources.add(TerraformResource(
                                                            type = typeOrName.toString(),
                                                            name = name.toString(),
                                                            properties = properties
                                                        ))
                                                    }
                                                }
                                            }
                                        }
                                        else -> println("PARSER: Unexpected instances type: ${instances?.javaClass}")
                                    }
                                }
                            }
                            is List<*> -> {
                                value.forEach { item ->
                                    println("PARSER: Processing list item: $item")
                                    if (item is Map<*, *>) {
                                        item.forEach { (typeOrName, instances) ->
                                            if (instances is Map<*, *>) {
                                                if (key == "module") {
                                                    @Suppress("UNCHECKED_CAST")
                                                    val properties = instances as Map<String, Any>
                                                    resources.add(TerraformResource(
                                                        type = "module",
                                                        name = typeOrName.toString(),
                                                        properties = properties
                                                    ))
                                                } else {
                                                    instances.forEach { (name, props) ->
                                                        if (props is Map<*, *>) {
                                                            @Suppress("UNCHECKED_CAST")
                                                            val properties = props as Map<String, Any>
                                                            resources.add(TerraformResource(
                                                                type = typeOrName.toString(),
                                                                name = name.toString(),
                                                                properties = properties
                                                            ))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "data" -> {
                        println("PARSER: Found data block (not processing)")
                    }
                }
            }
            
            println("PARSER: Found ${resources.size} resources")
            return resources
            
        } catch (e: Exception) {
            println("PARSER: Error parsing HCL content: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    fun convertToBlocks(resources: List<TerraformResource>): List<Block> {
        return resources.map { resource ->
            val resourceType = when (resource.type) {
                "module" -> ResourceType.UNKNOWN
                else -> ResourceType.fromResourceName(resource.type)
            }

            // Convert properties to string values for our Block class
            val stringProperties = resource.properties.mapValues { (_, value) ->
                when (value) {
                    is Map<*, *> -> "{${value.entries.joinToString(", ") { "${it.key} = ${it.value}" }}}"
                    is List<*> -> "[${value.joinToString(", ")}]"
                    else -> value.toString()
                }
            }

            Block(
                id = "${resource.type}_${resource.name}",
                type = determineBlockType(resourceType),
                content = when (resource.type) {
                    "module" -> "Module: ${resource.name}"
                    else -> "${resourceType.displayName}: ${resource.name}"
                },
                resourceType = resourceType,
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
} 