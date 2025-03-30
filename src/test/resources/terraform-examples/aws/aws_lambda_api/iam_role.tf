# IAM role for the Lambda function
resource "aws_iam_role" "this" {
  name = "${local.prefix_with_domain}"
  tags = {
    Name = "lambda-role"
    Environment = "test"
  }

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": [
          "lambda.amazonaws.com",
          "edgelambda.amazonaws.com"
        ]
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
} 