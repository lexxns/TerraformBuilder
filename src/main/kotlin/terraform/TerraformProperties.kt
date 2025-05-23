package terraformbuilder.terraform

import terraformbuilder.ResourceType
import terraformbuilder.components.block.Block

/**
 * Represents a property for a Terraform resource
 */
// TODO support remaining atributes e.g. computed
// See: https://developer.hashicorp.com/terraform/plugin/sdkv2/schemas/schema-behaviors
data class TerraformProperty(
    val name: String,
    val type: PropertyType,
    val default: String? = null,
    val required: Boolean = false,
    val deprecated: Boolean = false,
    val description: String = "",
    val options: List<String> = emptyList() // For enum types
)

enum class PropertyType {
    STRING, NUMBER, BOOLEAN, ENUM, ARRAY, MAP, SET, JSON
}

// Map of block types to their available properties
object TerraformProperties {
    private val schemaLoader = TerraformSchemaLoader()
    private var currentSchema: Pair<List<ResourceType>, Map<ResourceType, List<TerraformProperty>>>? = null
    private var resourceDescriptions: Map<ResourceType, String> = emptyMap()

    /**
     * Initialize the schema. This should be called once at application startup.
     */
    fun initialize() {
        if (currentSchema == null) {
            currentSchema = loadLatestSchema()
        }
    }

    /**
     * Get the properties for a specific block
     */
    fun getPropertiesForBlock(block: Block): List<TerraformProperty> {
        if (currentSchema == null) {
            initialize()
        }
        return currentSchema!!.second[block.resourceType] ?: emptyList()
    }

    /**
     * Get the description for a specific resource type
     */
    fun getResourceDescription(resourceType: ResourceType): String {
        return resourceDescriptions[resourceType] ?: "No Resource Description Available."
    }

    /**
     * Set the resource descriptions (internal use only)
     */
    internal fun setResourceDescriptions(descriptions: Map<ResourceType, String>) {
        resourceDescriptions = descriptions
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
            throw e
        }
    }

    private fun getAvailableVersions(): List<String> {
        return try {
            TerraformProperties::class.java.classLoader
                .getResourceAsStream("terraform.aws")
                ?.bufferedReader()
                ?.readLines()
                ?.map { it.replace("_", ".") }
                ?.sortedByDescending { it }
                ?: emptyList()
        } catch (e: Exception) {
            println(e.message)
            emptyList()
        }
    }
} 