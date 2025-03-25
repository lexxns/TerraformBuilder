package terraformbuilder

/**
 * Enum representing AWS resource types that can be used in Terraform
 */
enum class ResourceType(val displayName: String, val resourceName: String) {
    LAMBDA_FUNCTION("Lambda Function", "aws_lambda_function"),
    EC2_INSTANCE("EC2 Instance", "aws_instance"),
    ECS_CLUSTER("ECS Cluster", "aws_ecs_cluster"),
    ECS_SERVICE("ECS Service", "aws_ecs_service"),
    DYNAMODB_TABLE("DynamoDB Table", "aws_dynamodb_table"),
    RDS_INSTANCE("RDS Instance", "aws_db_instance"),
    S3_BUCKET("S3 Bucket", "aws_s3_bucket"),
    ELASTICACHE("ElastiCache", "aws_elasticache_cluster"),
    VPC("VPC", "aws_vpc"),
    SUBNET("Subnet", "aws_subnet"),
    SECURITY_GROUP("Security Group", "aws_security_group"),
    ROUTE_TABLE("Route Table", "aws_route_table"),
    IAM_ROLE("IAM Role", "aws_iam_role"),
    IAM_POLICY("IAM Policy", "aws_iam_policy"),
    KMS_KEY("KMS Key", "aws_kms_key"),
    SECRETS_MANAGER("Secrets Manager", "aws_secretsmanager_secret"),
    API_GATEWAY("API Gateway", "aws_api_gateway_rest_api"),
    SQS_QUEUE("SQS Queue", "aws_sqs_queue"),
    SNS_TOPIC("SNS Topic", "aws_sns_topic"),
    EVENTBRIDGE_RULE("EventBridge Rule", "aws_cloudwatch_event_rule"),
    CLOUDWATCH_LOG_GROUP("CloudWatch Log Group", "aws_cloudwatch_log_group"),
    CLOUDWATCH_ALARM("CloudWatch Alarm", "aws_cloudwatch_metric_alarm"),
    XRAY_TRACE("X-Ray Trace", "aws_xray_sampling_rule"),
    CLOUDWATCH_DASHBOARD("CloudWatch Dashboard", "aws_cloudwatch_dashboard"),
    ROUTE53_RECORD("Route53 Record", "aws_route53_record"),
    ACM_CERTIFICATE("ACM Certificate", "aws_acm_certificate"),
    CLOUDFRONT_DISTRIBUTION("CloudFront Distribution", "aws_cloudfront_distribution"),
    UNKNOWN("Unknown Resource", "unknown");

    companion object {
        fun fromResourceName(resourceName: String): ResourceType {
            return values().find { it.resourceName == resourceName } ?: UNKNOWN
        }
    }
} 