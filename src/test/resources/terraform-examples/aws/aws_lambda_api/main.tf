resource "aws_api_gateway_rest_api" "this" {
  name        = "api-${var.api_domain}"
  description = "${var.comment_prefix}${var.api_domain}"
}

resource "aws_lambda_function" "this" {
  function_name    = "${var.function_name}"
  role             = "${aws_iam_role.this.arn}"
  handler          = "${var.function_handler}"
  runtime          = "${var.function_runtime}"
  timeout          = "${var.function_timeout}"
  memory_size      = "${var.memory_size}"
  publish          = true
  
  s3_bucket = "${var.function_s3_bucket}"
  s3_key    = "${var.function_zipfile}"
  
  environment {
    variables = "${var.function_env_vars}"
  }
} 