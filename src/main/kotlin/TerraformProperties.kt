package terraformbuilder

import com.fasterxml.jackson.databind.JsonNode

/**
 * Represents a property for a Terraform resource
 */
data class TerraformProperty(
    val name: String,
    val type: PropertyType,
    val default: String? = null,
    val required: Boolean = false,
    val description: String = "",
    val options: List<String> = emptyList() // For enum types
)

enum class PropertyType {
    STRING, NUMBER, BOOLEAN, ENUM
}

// Map of block types to their available properties
object TerraformProperties {
    private val schemaLoader = TerraformSchemaLoader()
    private var currentSchema: Pair<List<ResourceType>, Map<ResourceType, List<TerraformProperty>>> = loadLatestSchema()

    /**
     * Get all available resource types
     */
    fun getResourceTypes(): List<ResourceType> = currentSchema.first

    /**
     * Get the properties for a specific block
     */
    fun getPropertiesForBlock(block: Block): List<TerraformProperty> {
        return currentSchema.second[block.resourceType] ?: emptyList()
    }

    private fun loadLatestSchema(): Pair<List<ResourceType>, Map<ResourceType, List<TerraformProperty>>> {
        return try {
            val latestVersion = getAvailableVersions().firstOrNull()
                ?: throw IllegalStateException("No AWS provider versions found")
                
            val schema = schemaLoader.loadProviderSchema(latestVersion)
            // TODO support multiple schemas
            val resourceTypes = ResourceType.entries
            Pair(resourceTypes, schema.second)
        } catch (e: Exception) {
            println("Failed to load schema: ${e.message}")
            Pair(emptyList(), fallbackProperties())
        }
    }

    private fun getAvailableVersions(): List<String> {
        return try {
            TerraformProperties::class.java.classLoader
                .getResourceAsStream("terraform.aws")
                ?.bufferedReader()
                ?.readLines()
                ?.filter { it.endsWith("schema.json") }
                ?.map { it.substringBeforeLast("/schema.json").replace("_", ".") }
                ?.sortedByDescending { it }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun fallbackProperties(): Map<ResourceType, List<TerraformProperty>> {
        return mapOf(
            // ... existing hardcoded properties ...
        )
    }
} 