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
    private val blockTypeProperties = mapOf(
        // Compute resources
        Pair(ResourceType.LAMBDA_FUNCTION, listOf(
            TerraformProperty("function_name", PropertyType.STRING, required = true, description = "Name of the Lambda function"),
            TerraformProperty("runtime", PropertyType.ENUM, default = "nodejs18.x", required = true, 
                description = "Runtime environment", 
                options = listOf("nodejs18.x", "nodejs16.x", "python3.9", "python3.8", "java11", "go1.x", "ruby2.7")),
            TerraformProperty("handler", PropertyType.STRING, default = "index.handler", required = true, 
                description = "Function entry point"),
            TerraformProperty("memory_size", PropertyType.NUMBER, default = "128", description = "Memory allocation in MB"),
            TerraformProperty("timeout", PropertyType.NUMBER, default = "3", description = "Timeout in seconds"),
            TerraformProperty("publish", PropertyType.BOOLEAN, default = "false", description = "Publish new version")
        )),
        Pair(ResourceType.EC2_INSTANCE, listOf(
            TerraformProperty("instance_type", PropertyType.STRING, default = "t2.micro", required = true, description = "EC2 instance type"),
            TerraformProperty("ami", PropertyType.STRING, required = true, description = "AMI ID to use for the instance"),
            TerraformProperty("key_name", PropertyType.STRING, description = "Key pair name for SSH access"),
            TerraformProperty("monitoring", PropertyType.BOOLEAN, default = "false", description = "Enable detailed monitoring")
        )),
        
        // Database resources
        Pair(ResourceType.DYNAMODB_TABLE, listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the DynamoDB table"),
            TerraformProperty("billing_mode", PropertyType.ENUM, default = "PROVISIONED", 
                options = listOf("PROVISIONED", "PAY_PER_REQUEST"), 
                description = "Controls how you are charged for read and write throughput"),
            TerraformProperty("read_capacity", PropertyType.NUMBER, default = "5", description = "Read capacity units"),
            TerraformProperty("write_capacity", PropertyType.NUMBER, default = "5", description = "Write capacity units")
        )),
        Pair(ResourceType.RDS_INSTANCE, listOf(
            TerraformProperty("allocated_storage", PropertyType.NUMBER, default = "10", required = true, description = "Allocated storage in gigabytes"),
            TerraformProperty("engine", PropertyType.ENUM, required = true, 
                options = listOf("mysql", "postgres", "mariadb", "oracle-ee", "sqlserver-ee"),
                description = "Database engine"),
            TerraformProperty("instance_class", PropertyType.STRING, default = "db.t3.micro", required = true, description = "Database instance type"),
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the database"),
            TerraformProperty("username", PropertyType.STRING, required = true, description = "Master username"),
            TerraformProperty("password", PropertyType.STRING, required = true, description = "Master password"),
            TerraformProperty("skip_final_snapshot", PropertyType.BOOLEAN, default = "true", description = "Skip final snapshot before deletion")
        )),
        Pair(ResourceType.S3_BUCKET, listOf(
            TerraformProperty("bucket", PropertyType.STRING, description = "Bucket name (if not specified, a random name will be used)"),
            TerraformProperty("acl", PropertyType.ENUM, default = "private", 
                options = listOf("private", "public-read", "public-read-write", "authenticated-read"),
                description = "Canned ACL for the bucket"),
            TerraformProperty("versioning_enabled", PropertyType.BOOLEAN, default = "false", description = "Enable versioning"),
            TerraformProperty("force_destroy", PropertyType.BOOLEAN, default = "false", description = "Allow deletion of non-empty bucket")
        )),
        
        // Networking resources
        Pair(ResourceType.VPC, listOf(
            TerraformProperty("cidr_block", PropertyType.STRING, required = true, default = "10.0.0.0/16", description = "CIDR block for the VPC"),
            TerraformProperty("enable_dns_support", PropertyType.BOOLEAN, default = "true", description = "Enable DNS support"),
            TerraformProperty("enable_dns_hostnames", PropertyType.BOOLEAN, default = "false", description = "Enable DNS hostnames")
        )),
        Pair(ResourceType.SECURITY_GROUP, listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the security group"),
            TerraformProperty("description", PropertyType.STRING, default = "Managed by Terraform", description = "Description of the security group"),
            TerraformProperty("vpc_id", PropertyType.STRING, required = true, description = "VPC ID")
        )),
        
        // Security resources
        Pair(ResourceType.IAM_ROLE, listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the IAM role"),
            TerraformProperty("description", PropertyType.STRING, description = "Description of the IAM role"),
            TerraformProperty("assume_role_policy", PropertyType.STRING, required = true, description = "Policy that grants an entity permission to assume the role")
        )),
        Pair(ResourceType.KMS_KEY, listOf(
            TerraformProperty("description", PropertyType.STRING, description = "Description of the KMS key"),
            TerraformProperty("deletion_window_in_days", PropertyType.NUMBER, default = "10", description = "Duration in days after which the key is deleted"),
            TerraformProperty("enable_key_rotation", PropertyType.BOOLEAN, default = "false", description = "Enable automatic key rotation")
        )),
        
        // Integration resources
        Pair(ResourceType.API_GATEWAY, listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the API Gateway"),
            TerraformProperty("description", PropertyType.STRING, description = "Description of the API Gateway"),
            TerraformProperty("endpoint_configuration", PropertyType.ENUM, default = "EDGE", 
                options = listOf("EDGE", "REGIONAL", "PRIVATE"),
                description = "Endpoint configuration type")
        )),
        Pair(ResourceType.SQS_QUEUE, listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the SQS queue"),
            TerraformProperty("delay_seconds", PropertyType.NUMBER, default = "0", description = "Time in seconds that delivery will be delayed"),
            TerraformProperty("max_message_size", PropertyType.NUMBER, default = "262144", description = "Maximum message size in bytes"),
            TerraformProperty("message_retention_seconds", PropertyType.NUMBER, default = "345600", description = "Message retention period in seconds")
        )),
        
        // Monitoring resources
        Pair(ResourceType.CLOUDWATCH_LOG_GROUP, listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the log group"),
            TerraformProperty("retention_in_days", PropertyType.NUMBER, default = "30", description = "Log retention period in days")
        )),
        Pair(ResourceType.CLOUDWATCH_ALARM, listOf(
            TerraformProperty("alarm_name", PropertyType.STRING, required = true, description = "Name of the alarm"),
            TerraformProperty("comparison_operator", PropertyType.ENUM, required = true,
                options = listOf("GreaterThanOrEqualToThreshold", "GreaterThanThreshold", "LessThanThreshold", "LessThanOrEqualToThreshold"),
                description = "Comparison operator for the alarm"),
            TerraformProperty("evaluation_periods", PropertyType.NUMBER, default = "1", description = "Number of periods to evaluate"),
            TerraformProperty("threshold", PropertyType.NUMBER, required = true, description = "Threshold value for the alarm")
        ))
    )
    
    // Helper method to get properties for a given block
    fun getPropertiesForBlock(block: Block): List<TerraformProperty> {
        return blockTypeProperties[block.resourceType] ?: emptyList()
    }
} 