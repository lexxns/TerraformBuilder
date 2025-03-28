package terraformbuilder.codegen

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

class ResourceTypeGenerator {
    private val objectMapper = ObjectMapper()

    fun generateResourceTypeEnum(schemaFile: File, outputFile: File) {
        val schema = objectMapper.readTree(schemaFile)
        val resourceSchemas = schema
            .path("provider_schemas")
            .path("registry.terraform.io/hashicorp/aws")
            .path("resource_schemas")

        val resourceTypes = resourceSchemas.fieldNames().asSequence()
            .filter { it.startsWith("aws_") }
            .map { resourceName ->
                val enumName = resourceName
                    .removePrefix("aws_")
                    .split("_")
                    .joinToString("_") { it.uppercase() }
                val displayName = resourceName
                    .removePrefix("aws_")
                    .split("_")
                    .joinToString(" ") { it.capitalize() }
                Triple(enumName, displayName, resourceName)
            }
            .sortedBy { it.first }
            .toList()

        val enumCode = buildString {
            append("""
                package terraformbuilder
                
                /**
                 * Generated enum representing AWS resource types that can be used in Terraform
                 */
                enum class ResourceType(val displayName: String, val resourceName: String) {
            """.trimIndent())
            append("\n")
            
            // Add all resource types
            resourceTypes.forEach { (enumName, displayName, resourceName) ->
                append("    $enumName(\"$displayName\", \"$resourceName\"),\n")
            }
            
            // Add UNKNOWN entry
            append("    UNKNOWN(\"Unknown Resource\", \"unknown\");\n\n")
            
            // Add companion object
            append("""
                    companion object {
                        fun fromResourceName(resourceName: String): ResourceType {
                            return entries.find { it.resourceName == resourceName } ?: UNKNOWN
                        }
                        fun fromDisplayName(displayName: String): ResourceType {
                            return entries.find { it.displayName == displayName } ?: UNKNOWN
                        }
                    }
                }
            """.trimIndent())
        }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(enumCode)
    }
} 