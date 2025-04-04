package terraformbuilder.terraformbuilder

import terraformbuilder.ResourceType
import terraformbuilder.components.Block
import terraformbuilder.components.BlockType
import terraformbuilder.components.Connection
import terraformbuilder.terraform.TerraformVariable
import terraformbuilder.terraform.VariableType
import java.io.File

/**
 * Class for generating Terraform code from blocks, connections, and variables
 */
class TerraformCodeGenerator {
    /**
     * Generate Terraform code from the given blocks, connections, and variables.
     * @param blocks List of Block objects representing resources
     * @param connections List of Connection objects representing dependencies
     * @param variables List of TerraformVariable objects
     * @param outputDir Directory where Terraform files will be written
     */
    fun generateCode(
        blocks: List<Block>, connections: List<Connection>, variables: List<TerraformVariable>, outputDir: File
    ) {
        // Ensure output directory exists
        outputDir.mkdirs()

        // Process connections to establish dependencies between blocks
        val blocksCopy = blocks.map { it.copy() }
        processConnectionsIntoDependencies(blocksCopy, connections)

        // Generate provider configuration
        val providerConfig = generateProviderConfig()

        // Generate resource declarations
        val resourceDeclarations = blocksCopy.map {
            generateResourceDeclaration(it, variables)
        }

        // Generate variable declarations
        val variableDeclarations = variables.map {
            generateVariableDeclaration(it)
        }

        // Add default variables if needed
        val defaultVariables = generateDefaultVariables()

        // Generate output declarations for significant resources
        val outputDeclarations = generateOutputs(blocksCopy)

        // Write to files
        File(outputDir, "provider.tf").writeText(providerConfig)
        File(outputDir, "main.tf").writeText(resourceDeclarations.joinToString("\n\n"))
        File(outputDir, "variables.tf").writeText(
            (defaultVariables + variableDeclarations).joinToString("\n\n")
        )
        File(outputDir, "outputs.tf").writeText(outputDeclarations.joinToString("\n\n"))

        println("Terraform code generated successfully in ${outputDir.absolutePath}")
    }

    /**
     * Generates the AWS provider configuration.
     */
    private fun generateProviderConfig(): String {
        return """
        provider "aws" {
          region = var.aws_region
        }
        """.trimIndent()
    }

    /**
     * Generates default variables that should be included in every project.
     */
    private fun generateDefaultVariables(): List<String> {
        return listOf(
            """
            variable "aws_region" {
              description = "The AWS region to deploy resources into"
              type        = string
              default     = "us-west-2"
            }
            """.trimIndent()
        )
    }

    /**
     * Generates a Terraform resource declaration from a Block.
     */
    private fun generateResourceDeclaration(block: Block, variables: List<TerraformVariable>): String {
        val resourceType = block.resourceType.resourceName
        val resourceName = formatResourceName(block.content)

        // Filter out empty properties and format each property
        val properties =
            block.properties.entries.filter { it.value.isNotEmpty() }.joinToString("\n  ") { (key, value) ->
                "$key = ${formatPropertyValue(key, value, block.resourceType, variables)}"
            }

        return """
        resource "$resourceType" "$resourceName" {
          $properties
        }
        """.trimIndent()
    }

    /**
     * Formats a block content string into a valid Terraform resource name.
     */
    private fun formatResourceName(content: String): String {
        // Convert to valid Terraform resource name: lowercase, underscores, no special chars
        return content.lowercase().replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
    }

    /**
     * Formats a property value based on its type and context.
     */
    /**
     * Formats a property value based on its type, context, and potential variable references.
     */
    private fun formatPropertyValue(
        key: String, value: String, resourceType: ResourceType, variables: List<TerraformVariable>
    ): String {
        // Check if this is empty or null
        if (value.isBlank()) {
            return "\"\""
        }

        // Check if this is a variable reference
        val matchingVar = variables.find { it.name == value }
        if (matchingVar != null) {
            return "var.${matchingVar.name}"
        }

        // Handle resource references (created by processConnectionsIntoDependencies)
        if (value.contains(".")) {
            val parts = value.split(".")
            if (parts.size >= 3 && parts[0].startsWith("aws_")) {
                return value  // This is already a properly formatted reference
            }
        }

        // Special handling for policy documents and JSON structures
        if (isPolicyDocument(key, value)) {
            return formatPolicyDocument(value)
        }

        // Special handling for tags
        if (key == "tags" && value.contains("=")) {
            return formatTags(value)
        }

        // Special handling for lists that might contain references
        if (value.startsWith("[") && value.endsWith("]")) {
            return formatListValue(value, variables)
        }

        // Special handling for depends_on
        if (key == "depends_on") {
            return value  // Already formatted properly by processConnectionsIntoDependencies
        }

        // Handle resource references embedded in strings
        if (value.contains("\${") && value.contains("}")) {
            return formatInterpolatedString(value)
        }

        // Standard type detection with better number handling
        return when {
            value.startsWith("{") && value.endsWith("}") -> value  // Map/JSON
            value == "true" || value == "false" -> value  // Boolean
            isNumber(value) -> value  // Number
            else -> "\"${escapeStringValue(value)}\""  // String
        }
    }

    /**
     * Checks if a string represents a valid number.
     */
    private fun isNumber(value: String): Boolean {
        return try {
            value.toDouble()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    /**
     * Escapes special characters in a string value.
     */
    private fun escapeStringValue(value: String): String {
        return value.replace("\"", "\\\"")
    }

    /**
     * Formats a list value, handling any references inside it.
     */
    private fun formatListValue(value: String, variables: List<TerraformVariable>): String {
        // Parse the list content
        val content = value.trim('[', ']')
        if (content.isBlank()) return "[]"

        val items = content.split(",").map { it.trim() }

        // Process each item
        val formattedItems = items.map { item ->
            when {
                // Variable reference
                variables.any { v -> v.name == item } -> "var.${item}"

                // Resource reference
                item.contains(".") && item.split(".").size >= 3 -> item

                // Basic types
                item == "true" || item == "false" -> item  // Boolean
                isNumber(item) -> item  // Number
                else -> "\"${escapeStringValue(item)}\""  // String
            }
        }

        return "[${formattedItems.joinToString(", ")}]"
    }

    /**
     * Formats a string that contains Terraform interpolation.
     */
    private fun formatInterpolatedString(value: String): String {
        // If it's already using the newer Terraform interpolation syntax
        if (value.contains("\${") && !value.contains("\${")) {
            return "\"$value\""
        }

        // Convert from older to newer syntax if needed
        return "\"${value.replace("\${", "\${")}\""
    }

    /**
     * Determines if a property is a policy document based on name and value.
     */
    private fun isPolicyDocument(key: String, value: String): Boolean {
        val policyKeys = listOf("policy", "assume_role_policy", "inline_policy", "document")
        return (policyKeys.any { key.contains(it, ignoreCase = true) } && value.startsWith("{") && value.endsWith("}"))
    }

    /**
     * Formats a policy document using jsonencode.
     */
    private fun formatPolicyDocument(value: String): String {
        return "jsonencode(${value})"
    }

    /**
     * Formats tags into a Terraform map.
     */
    private fun formatTags(value: String): String {
        // Extract the key-value pairs from the JSON-like string
        // Assumes format like {key1 = value1, key2 = value2}
        val content = value.trim('{', '}')
        val pairs = content.split(",").map { it.trim() }

        // Format as a Terraform map
        return "{\n    ${pairs.joinToString(",\n    ")}\n  }"
    }

    /**
     * Processes connections into resource dependencies by examining schema properties
     * and inferring relationships dynamically.
     */
    private fun processConnectionsIntoDependencies(blocks: List<Block>, connections: List<Connection>) {
        // Process each connection
        connections.forEach { connection ->
            // Find the actual blocks from our copied list
            val sourceBlock = blocks.find { it.id == connection.sourceBlock.id }
            val targetBlock = blocks.find { it.id == connection.targetBlock.id }

            if (sourceBlock != null && targetBlock != null) {
                val sourceType = sourceBlock.resourceType
                val targetType = targetBlock.resourceType

                // Format resource names for reference
                val sourceResourceName = formatResourceName(sourceBlock.content)
                val sourceResourceType = sourceBlock.resourceType.resourceName

                // Create a reference to the source resource
                val sourceRef = "${sourceResourceType}.${sourceResourceName}"

                // Get target resource properties from schema
                val targetProperties = terraformbuilder.terraform.TerraformProperties.getPropertiesForBlock(targetBlock)

                // Try to find a matching property in the target that might accept a reference to the source
                val possibleReferenceProps = findPossibleReferenceProperties(targetProperties, sourceType)

                if (possibleReferenceProps.isNotEmpty()) {
                    // Use the first matching property
                    val referenceProperty = possibleReferenceProps.first()

                    // Determine the appropriate attribute to reference (id, arn, etc.)
                    val sourceAttribute = determineSourceAttribute(referenceProperty.name, sourceType)

                    // Generate the reference expression
                    val referenceExpr = "${sourceRef}.${sourceAttribute}"

                    // Update the target block's property
                    targetBlock.setProperty(referenceProperty.name, referenceExpr)

                    println("Created reference from ${targetType.name} to ${sourceType.name} via property ${referenceProperty.name}")
                } else {
                    // No specific property found - use depends_on instead
                    val currentDependsOn = targetBlock.getProperty("depends_on")

                    val dependsOnExpr = if (currentDependsOn.isNullOrBlank()) {
                        "[${sourceRef}]"
                    } else {
                        // Parse and update existing depends_on list
                        val content = currentDependsOn.trim('[', ']')
                        val dependencies = content.split(",").map { it.trim() }

                        // Only add if not already present
                        if (!dependencies.contains(sourceRef)) {
                            "[${(dependencies + sourceRef).joinToString(", ")}]"
                        } else {
                            currentDependsOn
                        }
                    }

                    targetBlock.setProperty("depends_on", dependsOnExpr)
                    println("Added depends_on relationship from ${targetType.name} to ${sourceType.name}")
                }
            }
        }
    }

    /**
     * Finds properties in the target resource that might be references to the source resource.
     */
    private fun findPossibleReferenceProperties(
        properties: List<terraformbuilder.terraform.TerraformProperty>, sourceType: ResourceType
    ): List<terraformbuilder.terraform.TerraformProperty> {
        // Common patterns for reference properties based on source resource type
        val sourceTypeName = sourceType.name.lowercase()
        val possiblePatterns = listOf(
            "${sourceTypeName}_id",
            "${sourceTypeName.replace("_", "")}id",
            "${sourceTypeName}_arn",
            "${sourceTypeName.replace("_", "")}arn"
        )

        // Special cases for common AWS resource relationships
        val specialPatterns = when (sourceType) {
            ResourceType.VPC -> listOf("vpc_id")
            ResourceType.SUBNET -> listOf("subnet_id", "subnet_ids")
            ResourceType.SECURITY_GROUP -> listOf("security_group_id", "security_group_ids", "vpc_security_group_ids")
            ResourceType.IAM_ROLE -> listOf("role", "role_arn", "iam_role_arn")
            ResourceType.S3_BUCKET -> listOf("bucket", "s3_bucket", "bucket_name")
            ResourceType.LAMBDA_FUNCTION -> listOf("function_name", "lambda_function_name", "function_arn")
            ResourceType.API_GATEWAY_REST_API -> listOf("rest_api_id", "api_id")
            ResourceType.DYNAMODB_TABLE -> listOf("table_name", "dynamodb_table_name")
            else -> emptyList()
        }

        // Find properties that match our patterns
        val matchingProps = properties.filter { prop ->
            val propName = prop.name.lowercase()
            (possiblePatterns.any { pattern -> propName == pattern || propName.endsWith("_$pattern") } || specialPatterns.any { pattern ->
                propName == pattern || propName.endsWith(
                    "_$pattern"
                )
            })
        }

        // If no direct matches, look for properties that contain the source type name
        if (matchingProps.isEmpty()) {
            return properties.filter { prop ->
                val propName = prop.name.lowercase()
                propName.contains(sourceTypeName) || propName.contains(sourceTypeName.replace("_", ""))
            }
        }

        return matchingProps
    }

    /**
     * Determines the appropriate source attribute to reference based on the target property.
     */
    private fun determineSourceAttribute(propertyName: String, sourceType: ResourceType): String {
        // Extract the suffix pattern (_id, _arn, etc.) to determine what attribute to reference
        val lowerName = propertyName.lowercase()

        return when {
            lowerName.endsWith("_arn") || lowerName.endsWith("arn") -> "arn"
            lowerName.endsWith("_name") || lowerName.endsWith("name") -> "name"
            lowerName == "bucket" || lowerName == "s3_bucket" -> "bucket"
            lowerName == "role" -> "arn"
            else -> "id" // Default to 'id' which is the most common attribute
        }
    }

    /**
     * Generates a Terraform variable declaration.
     */
    private fun generateVariableDeclaration(variable: TerraformVariable): String {
        val defaultValue = variable.defaultValue?.let {
            "  default     = ${formatVariableDefault(it, variable.type)}"
        } ?: ""

        val sensitive = if (variable.sensitive) "  sensitive   = true" else ""

        return """
        variable "${variable.name}" {
          description = "${variable.description}"
          type        = ${mapVariableType(variable.type)}
        ${defaultValue}
        ${sensitive}
        }
        """.trimIndent()
    }

    /**
     * Maps VariableType to Terraform type syntax.
     */
    private fun mapVariableType(type: VariableType): String {
        return when (type) {
            VariableType.STRING -> "string"
            VariableType.NUMBER -> "number"
            VariableType.BOOL -> "bool"
            VariableType.LIST -> "list(string)"
            VariableType.MAP -> "map(string)"
        }
    }

    /**
     * Formats a variable's default value based on its type.
     */
    private fun formatVariableDefault(value: String, type: VariableType): String {
        return when (type) {
            VariableType.STRING -> "\"$value\""
            VariableType.NUMBER, VariableType.BOOL -> value
            VariableType.LIST -> {
                if (value.startsWith("[") && value.endsWith("]")) value else "[$value]"
            }

            VariableType.MAP -> {
                if (value.startsWith("{") && value.endsWith("}")) value else "{ $value }"
            }
        }
    }

    /**
     * Generates output declarations for significant resources.
     */
    private fun generateOutputs(blocks: List<Block>): List<String> {
        val outputs = mutableListOf<String>()

        // Generate outputs for selected resource types
        blocks.forEach { block ->
            val resourceName = formatResourceName(block.content)
            val resourceType = block.resourceType.resourceName

            when (block.type) {
                BlockType.LOAD_BALANCER -> {
                    outputs.add(
                        """
                    output "${resourceName}_dns_name" {
                      description = "DNS name of the load balancer ${block.content}"
                      value       = ${resourceType}.${resourceName}.dns_name
                    }
                    """.trimIndent()
                    )
                }

                BlockType.EC2 -> {
                    outputs.add(
                        """
                    output "${resourceName}_public_ip" {
                      description = "Public IP address of the EC2 instance ${block.content}"
                      value       = ${resourceType}.${resourceName}.public_ip
                    }
                    """.trimIndent()
                    )
                }

                BlockType.RDS -> {
                    outputs.add(
                        """
                    output "${resourceName}_endpoint" {
                      description = "Endpoint of the RDS instance ${block.content}"
                      value       = ${resourceType}.${resourceName}.endpoint
                    }
                    """.trimIndent()
                    )
                }

                BlockType.LAMBDA -> {
                    outputs.add(
                        """
                    output "${resourceName}_function_name" {
                      description = "Name of the Lambda function ${block.content}"
                      value       = ${resourceType}.${resourceName}.function_name
                    }
                    """.trimIndent()
                    )
                }

                BlockType.API_GATEWAY -> {
                    outputs.add(
                        """
                    output "${resourceName}_invoke_url" {
                      description = "Invoke URL for the API Gateway ${block.content}"
                      value       = ${resourceType}.${resourceName}.execution_arn
                    }
                    """.trimIndent()
                    )
                }
                // Add outputs for other significant resource types
                else -> {}
            }
        }

        return outputs
    }
}