resource "aws_api_gateway_deployment" "this" {
  rest_api_id = "${aws_api_gateway_rest_api.this.id}"
  stage_name  = "${var.stage_name}"
  
  variables = {
    "deployed_at" = "${timestamp()}"
  }
  
  depends_on = [
    "aws_api_gateway_integration_response.proxy_root_response_200",
    "aws_api_gateway_integration_response.proxy_response_200",
  ]
}

resource "aws_api_gateway_stage" "this" {
  rest_api_id   = "${aws_api_gateway_rest_api.this.id}"
  deployment_id = "${aws_api_gateway_deployment.this.id}"
  stage_name    = "${var.stage_name}"
} 