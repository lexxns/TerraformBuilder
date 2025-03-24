package terraformbuilder

/**
 * Enum representing AWS resource types that can be used in Terraform
 */
enum class ResourceType(val displayName: String) {
    LAMBDA_FUNCTION("Lambda Function"),
    EC2_INSTANCE("EC2 Instance"),
    ECS_CLUSTER("ECS Cluster"),
    ECS_SERVICE("ECS Service"),
    DYNAMODB_TABLE("DynamoDB Table"),
    RDS_INSTANCE("RDS Instance"),
    S3_BUCKET("S3 Bucket"),
    ELASTICACHE("ElastiCache"),
    VPC("VPC"),
    SUBNET("Subnet"),
    SECURITY_GROUP("Security Group"),
    ROUTE_TABLE("Route Table"),
    IAM_ROLE("IAM Role"),
    IAM_POLICY("IAM Policy"),
    KMS_KEY("KMS Key"),
    SECRETS_MANAGER("Secrets Manager"),
    API_GATEWAY("API Gateway"),
    SQS_QUEUE("SQS Queue"),
    SNS_TOPIC("SNS Topic"),
    EVENTBRIDGE_RULE("EventBridge Rule"),
    CLOUDWATCH_LOG_GROUP("CloudWatch Log Group"),
    CLOUDWATCH_ALARM("CloudWatch Alarm"),
    XRAY_TRACE("X-Ray Trace"),
    CLOUDWATCH_DASHBOARD("CloudWatch Dashboard");
} 