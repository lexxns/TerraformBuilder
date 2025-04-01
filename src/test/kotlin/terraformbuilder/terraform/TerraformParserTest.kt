package terraformbuilder.terraform

import org.junit.Test
import terraformbuilder.ResourceType
import terraformbuilder.components.BlockType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerraformParserTest {

    @Test
    fun `test parse lambda API Terraform files`() {
        // Sample Terraform content from aws_lambda_api
        val mainTfContent = """
            resource "aws_api_gateway_rest_api" "this" {
              name        = "api-$\var.api_domain}"
              description = "$\var.comment_prefix}$\var.api_domain}"
            }
            
            resource "aws_lambda_function" "this" {
              function_name    = "$\var.name_prefix}$\replace(var.api_domain, "/[^a-zA-Z0-9]+/", "-")}"
              role             = "$\aws_iam_role.this.arn}"
              handler          = "$\var.function_handler}"
              runtime          = "$\var.function_runtime}"
              timeout          = "$\var.function_timeout}"
              memory_size      = "$\var.memory_size}"
              publish          = true
              
              s3_bucket = "$\var.function_s3_bucket}"
              s3_key    = "$\var.function_zipfile}"
              
              environment {
                variables = "$\var.function_env_vars}"
              }
            }
            
            resource "aws_s3_bucket" "uploads" {
              bucket = "my-uploads-bucket"
              acl    = "private"
              
              versioning {
                enabled = true
              }
            }
        """.trimIndent()

        val variablesTfContent = """
            variable "api_domain" {
              description = "Domain on which the Lambda will be made available (e.g. api.example.com)"
              type        = string
            }
            
            variable "comment_prefix" {
              description = "This will be included in comments for resources that are created"
              type        = string
              default     = "Lambda API: "
            }
            
            variable "function_s3_bucket" {
              description = "When provided, the zipfile is retrieved from an S3 bucket by this name instead"
              type        = string
              default     = ""
            }
            
            variable "function_zipfile" {
              description = "Path to a ZIP file that will be installed as the Lambda function"
              type        = string
            }
            
            variable "function_handler" {
              description = "Instructs Lambda on which function to invoke within the ZIP file"
              type        = string
              default     = "index.handler"
            }
            
            variable "function_runtime" {
              description = "Which Node.js version should Lambda use for this function"
              type        = string
              default     = "nodejs16.x"
            }
            
            variable "function_timeout" {
              description = "The amount of time your Lambda Function has to run in seconds"
              type        = string
              default     = "3"
            }
            
            variable "function_env_vars" {
              description = "Which env vars (if any) to invoke the Lambda with"
              type        = map(string)
              default     = {}
            }
            
            variable "memory_size" {
              description = "Amount of memory in MB your Lambda Function can use at runtime"
              type        = string
              default     = "128"
            }
            
            variable "name_prefix" {
              description = "Name prefix for resources"
              type        = string
              default     = "aws-lambda-api---"
            }
        """.trimIndent()

        // Parse the content
        val parser = TerraformParser()
        val mainResult = parser.parse(mainTfContent)
        val variablesResult = parser.parse(variablesTfContent)

        // Test resources
        assertEquals(3, mainResult.resources.size, "Should parse 3 resources")

        // Test API Gateway resource
        val apiGateway = mainResult.resources.find { it.type == "aws_api_gateway_rest_api" }
        assertNotNull(apiGateway, "Should find aws_api_gateway_rest_api resource")
        assertEquals("this", apiGateway.name)

        // Test Lambda function
        val lambdaFunction = mainResult.resources.find { it.type == "aws_lambda_function" }
        assertNotNull(lambdaFunction, "Should find aws_lambda_function resource")
        assertEquals("this", lambdaFunction.name)
        assertTrue(lambdaFunction.properties.containsKey("function_name"), "Lambda should have function_name property")
        assertTrue(lambdaFunction.properties.containsKey("runtime"), "Lambda should have runtime property")

        // Test S3 bucket
        val s3Bucket = mainResult.resources.find { it.type == "aws_s3_bucket" }
        assertNotNull(s3Bucket, "Should find aws_s3_bucket resource")
        assertEquals("uploads", s3Bucket.name)
        assertEquals("my-uploads-bucket", s3Bucket.properties["bucket"])

        // Test variables
        assertEquals(10, variablesResult.variables.size, "Should parse 10 variables")

        // Test specific variables
        val apiDomainVar = variablesResult.variables.find { it.name == "api_domain" }
        assertNotNull(apiDomainVar, "Should find api_domain variable")
        assertEquals(VariableType.STRING, apiDomainVar.type)
        assertEquals(
            "Domain on which the Lambda will be made available (e.g. api.example.com)",
            apiDomainVar.description
        )

        val functionEnvVarsVar = variablesResult.variables.find { it.name == "function_env_vars" }
        assertNotNull(functionEnvVarsVar, "Should find function_env_vars variable")
        assertEquals("map(string)", functionEnvVarsVar.type.toString().lowercase())

        // Test block conversion
        val blocks = parser.convertToBlocks(mainResult.resources)
        assertEquals(3, blocks.size, "Should create 3 blocks")

        // Test block types
        val apiGatewayBlock = blocks.find { it.resourceType == ResourceType.API_GATEWAY_REST_API }
        assertNotNull(apiGatewayBlock, "Should create API Gateway block")
        assertEquals(BlockType.INTEGRATION, apiGatewayBlock.type)

        val lambdaBlock = blocks.find { it.resourceType == ResourceType.LAMBDA_FUNCTION }
        assertNotNull(lambdaBlock, "Should create Lambda Function block")
        assertEquals(BlockType.LAMBDA, lambdaBlock.type)

        val s3Block = blocks.find { it.resourceType == ResourceType.S3_BUCKET }
        assertNotNull(s3Block, "Should create S3 Bucket block")
        assertEquals(BlockType.STORAGE, s3Block.type)
    }
} 