package terraformbuilder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream

class TerraformSchemaLoader {
    private val objectMapper = ObjectMapper()

    fun loadProviderSchema(version: String): Pair<JsonNode, Map<ResourceType, List<TerraformProperty>>> {
        val schemaStream = getSchemaForVersion(version)
        val root = objectMapper.readTree(schemaStream)
        
        val awsSchema = root
            .path("provider_schemas")
            .path("registry.terraform.io/hashicorp/aws")
            .path("resource_schemas")
            
        return Pair(awsSchema, buildResourceMap(awsSchema))
    }

    private fun getSchemaForVersion(version: String): InputStream {
        // Convert version format (e.g., "5.92.0" to "5_92_0")
        val formattedVersion = version.replace(".", "_")
        val resourcePath = "terraform.aws/$formattedVersion/schema.json"
        
        return javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException(
                "Schema not found for AWS provider version $version. " +
                "Available versions: ${listAvailableVersions()}"
            )
    }
    
    private fun listAvailableVersions(): List<String> {
        return javaClass.classLoader
            .getResourceAsStream("terraform.aws")
            ?.bufferedReader()
            ?.readLines()
            ?.filter { it.endsWith("schema.json") }
            ?.map { it.removeSuffix("/schema.json").replace("_", ".") }
            ?: emptyList()
    }

    private fun buildResourceMap(schema: JsonNode): Map<ResourceType, List<TerraformProperty>> {
        val result = mutableMapOf<ResourceType, List<TerraformProperty>>()
        
        ResourceType.entries.forEach { resourceType ->
            if (resourceType != ResourceType.UNKNOWN) {
                val resourceSchema = schema.path(resourceType.resourceName)
                if (!resourceSchema.isMissingNode) {
                    result[resourceType] = extractProperties(resourceSchema)
                }
            }
        }
        
        return result
    }
    
    private fun extractProperties(schema: JsonNode): List<TerraformProperty> {
        val properties = mutableListOf<TerraformProperty>()
        val attributes = schema.path("block").path("attributes")
        
        attributes.fields().forEach { (name, details) ->
            val type = when (details.path("type").asText()) {
                "string" -> PropertyType.STRING
                "number" -> PropertyType.NUMBER
                "bool" -> PropertyType.BOOLEAN
                else -> PropertyType.STRING // Default to string for complex types
            }
            
            properties.add(TerraformProperty(
                name = name,
                type = type,
                required = details.path("required").asBoolean(false),
                deprecated = details.path("deprecated").asBoolean(false),
                description = details.path("description").asText(""),
                // We could potentially extract options for enum types from validation blocks
                options = emptyList()
            ))
        }
        
        return properties
    }
} 