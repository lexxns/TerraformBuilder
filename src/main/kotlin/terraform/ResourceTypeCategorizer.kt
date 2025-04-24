package terraformbuilder.terraform

import terraformbuilder.ResourceType
import terraformbuilder.components.block.BlockType

/**
 * Handles the categorization of ResourceTypes into BlockTypes based on AWS product/service.
 */
class ResourceTypeCategorizer {
    // Product-based patterns for resource categorization
    private val albPatterns = listOf(
        "alb",
        "elb",
        "lb",
        "target_group",
        "listener",
        "certificate"
    )

    private val ec2Patterns = listOf(
        "ec2",
        "instance",
        "launch_template",
        "spot_instance",
        "spot_fleet",
        "placement_group",
        "capacity_reservation"
    )

    private val vpcPatterns = listOf(
        "vpc",
        "subnet",
        "route_table",
        "network_acl",
        "nat_gateway",
        "internet_gateway",
        "vpn_gateway",
        "vpn_connection",
        "vpc_endpoint",
        "vpc_peering"
    )

    private val securityPatterns = listOf(
        "security_group",
        "iam",
        "kms",
        "secretsmanager",
        "waf",
        "guardduty",
        "securityhub",
        "macie",
        "inspector",
        "detective"
    )

    private val lambdaPatterns = listOf(
        "lambda",
        "function",
        "layer",
        "event_source",
        "permission"
    )

    private val ecsPatterns = listOf(
        "ecs",
        "cluster",
        "service",
        "task_definition",
        "capacity_provider"
    )

    private val rdsPatterns = listOf(
        "rds",
        "db_instance",
        "db_cluster",
        "db_subnet_group",
        "db_parameter_group",
        "db_option_group",
        "db_snapshot"
    )

    private val dynamodbPatterns = listOf(
        "dynamodb",
        "table",
        "global_table",
        "table_item"
    )

    private val s3Patterns = listOf(
        "s3",
        "bucket",
        "bucket_policy",
        "bucket_notification",
        "bucket_public_access_block"
    )

    private val cloudwatchPatterns = listOf(
        "cloudwatch",
        "metric_alarm",
        "log_group",
        "dashboard",
        "event_rule",
        "event_target"
    )

    private val apigatewayPatterns = listOf(
        "api_gateway",
        "apigateway",
        "rest_api",
        "resource",
        "method",
        "integration_response",
        "deployment",
        "stage",
        "authorizer",
        "gateway_response",
        "model",
        "request_validator",
        "usage_plan",
        "usage_plan_key",
        "vpc_link",
        "api_key"
    )

    private val sqsPatterns = listOf(
        "sqs",
        "queue",
        "queue_policy"
    )

    private val snsPatterns = listOf(
        "sns",
        "topic",
        "subscription",
        "topic_policy"
    )

    private val kinesisPatterns = listOf(
        "kinesis",
        "stream",
        "firehose",
        "analytics"
    )

    // Manual overrides for specific resources that don't match patterns
    private val manualOverrides = mapOf(
        ResourceType.S3_BUCKET to BlockType.STORAGE,
        ResourceType.ELB to BlockType.LOAD_BALANCER,
        ResourceType.LB to BlockType.LOAD_BALANCER
    )

    /**
     * Determines the appropriate BlockType for a given ResourceType using a combination of
     * pattern matching and manual overrides.
     */
    fun determineBlockType(resourceType: ResourceType): BlockType {
        // Check manual overrides first
        manualOverrides[resourceType]?.let { return it }

        val resourceName = resourceType.resourceName.lowercase()

        // Check each product's patterns
        when {
            albPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.LOAD_BALANCER
            ec2Patterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.EC2
            vpcPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.VPC
            securityPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.SECURITY
            lambdaPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.LAMBDA
            ecsPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.ECS
            rdsPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.RDS
            dynamodbPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.DYNAMODB
            s3Patterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.STORAGE
            cloudwatchPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.MONITORING
            apigatewayPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.API_GATEWAY
            sqsPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.SQS
            snsPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.SNS
            kinesisPatterns.any { resourceName.contains(it.lowercase()) } -> return BlockType.KINESIS
        }

        // Default to INTEGRATION for unknown types
        return BlockType.INTEGRATION
    }
} 