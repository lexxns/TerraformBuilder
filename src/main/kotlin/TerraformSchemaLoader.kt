package terraformbuilder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.BufferedReader
import java.io.InputStreamReader

class TerraformSchemaLoader {
    fun loadProviderSchema(): Map<ResourceType, List<TerraformProperty>> {
        // Run terraform providers schema -json command
        val process = ProcessBuilder(
            "terraform", "providers", "schema", "-json"
        ).start()
        
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readText()
        
        return parseSchemaJson(output)
    }
    
    private fun parseSchemaJson(json: String): Map<ResourceType, List<TerraformProperty>> {
        val mapper = ObjectMapper()
        val root = mapper.readTree(json)
        
        // Navigate to AWS provider schema
        val awsSchema = root
            .path("provider_schemas")
            .path("registry.terraform.io/hashicorp/aws")
            .path("resource_schemas")
            
        return buildResourceMap(awsSchema)
    }
    
    private fun buildResourceMap(schema: JsonNode): Map<ResourceType, List<TerraformProperty>> {
        val result = mutableMapOf<ResourceType, List<TerraformProperty>>()
        
        // Map each resource type we care about
        ResourceType.values().forEach { resourceType ->
            val terraformName = resourceType.toTerraformName()
            val resourceSchema = schema.path(terraformName)
            
            if (!resourceSchema.isMissingNode) {
                result[resourceType] = extractProperties(resourceSchema)
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
                description = details.path("description").asText(""),
                // We could potentially extract options for enum types from validation blocks
                options = emptyList()
            ))
        }
        
        return properties
    }
} 