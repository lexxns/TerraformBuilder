package terraformbuilder.terraform

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import terraformbuilder.ResourceType
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

        val result = Pair(awsSchema, buildResourceMap(awsSchema))
        return result
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
        val descriptions = mutableMapOf<ResourceType, String>()

        ResourceType.entries.forEach { resourceType ->
            if (resourceType != ResourceType.UNKNOWN) {
                val resourceSchema = schema.path(resourceType.resourceName)
                if (!resourceSchema.isMissingNode) {
                    // Get the description from the block section
                    val blockNode = resourceSchema.path("components/block")
                    if (!blockNode.isMissingNode) {
                        val description = blockNode.path("description").asText("")
                        descriptions[resourceType] = description
                    }
                    result[resourceType] = extractProperties(resourceSchema)
                }
            }
        }

        // Store the descriptions in the TerraformProperties object
        TerraformProperties.setResourceDescriptions(descriptions)

        // Log which resources have properties with descriptions
        println("\nResources with properties containing descriptions:")
        result.forEach { (resourceType, properties) ->
            val propertiesWithDescriptions = properties.count { it.description.isNotEmpty() }
            if (propertiesWithDescriptions > 0) {
                println("${resourceType.name}: $propertiesWithDescriptions properties with descriptions")
            }
        }

        return result
    }

    private fun complexPropertyType(details: JsonNode): PropertyType {
        // Handle known complex types with explicit type arrays
        val typeStr = details.path("type").toString()

        return when {
            typeStr == "[\"map\",\"string\"]" -> PropertyType.MAP
            typeStr == "[\"set\",\"string\"]" -> PropertyType.SET
            typeStr.contains("policy") -> PropertyType.JSON  // Fallback detection for complex policy types
            else -> PropertyType.STRING  // Default for unrecognized complex types
        }
    }

    /**
     * Determines if a field likely contains a JSON policy document based on its name and description.
     */
    private fun isPolicyField(name: String, description: String): Boolean {
        // Common policy field name patterns
        val policyNamePatterns = listOf(
            "_policy$",
            "policy$",
            "policy_",
            "_document$",
            "document$",
            "^assume_role_",
            "^trust_"
        )

        // Check name against known patterns
        if (policyNamePatterns.any { pattern ->
                Regex(pattern).containsMatchIn(name.lowercase())
            }) {
            return true
        }

        // Check description for policy-related terms
        val descriptionKeywords = listOf(
            "policy document",
            "json",
            "iam policy",
            "policy statement",
            "trust relationship"
        )

        if (descriptionKeywords.any { keyword ->
                description.lowercase().contains(keyword.lowercase())
            }) {
            return true
        }

        return false
    }

    private fun extractProperties(schema: JsonNode): List<TerraformProperty> {
        val properties = mutableListOf<TerraformProperty>()
        val attributes = schema.path("components/block").path("attributes")

        attributes.fields().forEach { (name, details) ->
            val description = details.path("description").asText("")
            val type = when (details.path("type").asText()) {
                "string" -> {
                    // Check if this string might be a JSON policy
                    if (isPolicyField(name, description)) {
                        PropertyType.JSON
                    } else {
                        PropertyType.STRING
                    }
                }

                "number" -> PropertyType.NUMBER
                "bool" -> PropertyType.BOOLEAN
                else -> complexPropertyType(details)
            }

            properties.add(
                TerraformProperty(
                    name = name,
                    type = type,
                    required = details.path("required").asBoolean(false),
                    deprecated = details.path("deprecated").asBoolean(false),
                    description = description,
                    options = emptyList()
                )
            )
        }

        // Also process nested block_types, especially for inline policies
        processNestedBlocks(schema, properties)

        return properties
    }

    /**
     * Process nested blocks like inline_policy which might contain policy fields
     */
    private fun processNestedBlocks(schema: JsonNode, properties: MutableList<TerraformProperty>) {
        val blockTypes = schema.path("components/block").path("block_types")

        blockTypes.fields().forEach { (blockName, blockDetails) ->
            // For policy-related blocks, mark their policy fields as JSON
            if (blockName.contains("policy") || blockName.contains("document")) {
                val nestedAttributes = blockDetails.path("components/block").path("attributes")

                nestedAttributes.fields().forEach { (fieldName, fieldDetails) ->
                    val fieldDescription = fieldDetails.path("description").asText("")

                    if (fieldName == "policy" || fieldName == "document" ||
                        isPolicyField(fieldName, fieldDescription)
                    ) {

                        // Add this as a nested property with JSON type
                        properties.add(
                            TerraformProperty(
                                name = "$blockName.$fieldName",
                                type = PropertyType.JSON,
                                required = fieldDetails.path("required").asBoolean(false),
                                deprecated = fieldDetails.path("deprecated").asBoolean(false),
                                description = fieldDescription,
                                options = emptyList()
                            )
                        )
                    }
                }
            }
        }
    }
}