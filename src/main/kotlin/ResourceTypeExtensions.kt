package terraformbuilder

fun ResourceType.toTerraformName(): String {
    return when (this) {
        ResourceType.LAMBDA_FUNCTION -> "aws_lambda_function"
        ResourceType.EC2_INSTANCE -> "aws_instance"
        ResourceType.DYNAMODB_TABLE -> "aws_dynamodb_table"
        ResourceType.RDS_INSTANCE -> "aws_db_instance"
        ResourceType.S3_BUCKET -> "aws_s3_bucket"
        ResourceType.VPC -> "aws_vpc"
        ResourceType.SECURITY_GROUP -> "aws_security_group"
        ResourceType.IAM_ROLE -> "aws_iam_role"
        ResourceType.KMS_KEY -> "aws_kms_key"
        ResourceType.API_GATEWAY -> "aws_api_gateway_rest_api"
        ResourceType.SQS_QUEUE -> "aws_sqs_queue"
        ResourceType.CLOUDWATCH_LOG_GROUP -> "aws_cloudwatch_log_group"
        ResourceType.CLOUDWATCH_ALARM -> "aws_cloudwatch_metric_alarm"
    }
} 