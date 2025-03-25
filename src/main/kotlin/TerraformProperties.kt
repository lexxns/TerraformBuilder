package terraformbuilder

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
    private val blockTypeProperties: Map<ResourceType, List<TerraformProperty>> by lazy {
        try {
            schemaLoader.loadProviderSchema()
        } catch (e: Exception) {
            // Fallback to hardcoded properties if schema loading fails
            fallbackProperties()
        }
    }
    
    // Helper method to get properties for a given block
    fun getPropertiesForBlock(block: Block): List<TerraformProperty> {
        return blockTypeProperties[block.resourceType] ?: emptyList()
    }
    
    // Move existing hardcoded properties to a fallback method
    private fun fallbackProperties(): Map<ResourceType, List<TerraformProperty>> {
        return mapOf(
            // ... existing hardcoded properties ...
        )
    }
} 