# Permission policy for the Lambda function with jsonencode
resource "aws_iam_policy" "lambda_permissions" {
  name        = "${local.prefix_with_domain}-permissions"
  description = "IAM policy for the Lambda API Gateway function"
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject"
        ]
        Resource = "${var.bucket_arn}/*"
      }
    ]
  })
}

# Attach the policy to the IAM role
resource "aws_iam_role_policy_attachment" "lambda_policy_attachment" {
  role       = aws_iam_role.this.name
  policy_arn = aws_iam_policy.lambda_permissions.arn
} 