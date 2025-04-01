package terraformbuilder.github

import org.junit.Test
import terraformbuilder.ResourceType
import terraformbuilder.components.BlockType
import terraformbuilder.terraform.PropertyType
import terraformbuilder.terraform.TerraformParser
import terraformbuilder.terraform.TerraformProperties
import terraformbuilder.terraform.VariableType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GithubIntegrationTest {

    @Test
    fun `test loading and parsing Terraform files from GitHub`() {
        // 1. Parse a GitHub URL for the aws_lambda_api example
        val url = "https://github.com/lexxns/terraform-examples/tree/master/aws/aws_lambda_api"
        val repoInfo = GithubUrlParser.parse(url)

        // 2. Verify the parsed URL components
        assertNotNull(repoInfo, "Should successfully parse GitHub URL")
        assertEquals("lexxns", repoInfo.owner)
        assertEquals("terraform-examples", repoInfo.repo)
        assertEquals("master", repoInfo.branch)
        assertEquals("aws/aws_lambda_api", repoInfo.path)

        // 3. Use mock service to "load" Terraform files
        val mockService = MockGithubService()
        val files = mockService.loadTerraformFiles(repoInfo)

        // 4. Verify we got the expected number of files
        assertEquals(9, files.size, "Should load 9 Terraform files")

        // 5. Parse the files using TerraformParser
        val parser = TerraformParser()
        val resources = mutableListOf<terraformbuilder.terraform.TerraformResource>()
        val variables = mutableListOf<terraformbuilder.terraform.TerraformVariable>()

        files.forEach { fileContent ->
            val result = parser.parse(fileContent)
            resources.addAll(result.resources)
            variables.addAll(result.variables)
        }

        // 6. Verify resources were parsed correctly
        assertTrue(resources.isNotEmpty(), "Should find resources in the files")

        // Check for specific resources
        val lambdaResource = resources.find { it.type == "aws_lambda_function" }
        assertNotNull(lambdaResource, "Should find Lambda function resource")
        assertEquals("local_zipfile", lambdaResource.name)

        val apiGatewayResource = resources.find { it.type == "aws_api_gateway_rest_api" }
        assertNotNull(apiGatewayResource, "Should find API Gateway resource")

        // Check for IAM role with policy
        val iamRoleResource = resources.find { it.type == "aws_iam_role" }
        assertNotNull(iamRoleResource, "Should find IAM role resource")
        val assumeRolePolicy = iamRoleResource.properties["assume_role_policy"]
        assertNotNull(assumeRolePolicy, "IAM role should have an assume_role_policy")
        assertTrue(
            assumeRolePolicy.toString().contains("lambda.amazonaws.com"),
            "Assume role policy should contain lambda.amazonaws.com"
        )

        // Check for IAM policy resource
        val iamPolicyResource = resources.find { it.type == "aws_iam_policy" }
        assertNotNull(iamPolicyResource, "Should find IAM policy resource")
        val policyDocument = iamPolicyResource.properties["policy"]
        assertNotNull(policyDocument, "IAM policy should have a policy document")
        assertTrue(
            policyDocument.toString().contains("logs:CreateLogGroup"),
            "Policy document should contain expected permissions"
        )

        // 7. Verify variables were parsed correctly
        assertTrue(variables.isNotEmpty(), "Should find variables in the files")

        // Check for specific variables
        val apiDomainVariable = variables.find { it.name == "api_domain" }
        val functionHandler = variables.find { it.name == "function_handler" }
        assertNotNull(apiDomainVariable, "Should find api_domain variable")
        assertNotNull(functionHandler, "Should find function_handler variable")
        assertEquals(VariableType.STRING, apiDomainVariable.type)
        assertEquals(VariableType.STRING, functionHandler.type)

        // 8. Convert resources to blocks and verify
        val blocks = parser.convertToBlocks(resources)
        assertTrue(blocks.isNotEmpty(), "Should convert resources to blocks")

        // Verify block types
        val lambdaBlock = blocks.find { it.resourceType == ResourceType.LAMBDA_FUNCTION }
        assertNotNull(lambdaBlock, "Should create Lambda Function block")
        assertEquals(BlockType.LAMBDA, lambdaBlock.type)

        val apiGatewayBlock = blocks.find { it.resourceType == ResourceType.API_GATEWAY_REST_API }
        assertNotNull(apiGatewayBlock, "Should create API Gateway block")
        assertEquals(BlockType.INTEGRATION, apiGatewayBlock.type)

        // Verify IAM role is converted to SECURITY block
        val iamRoleBlock = blocks.find { it.resourceType == ResourceType.IAM_ROLE }
        assertNotNull(iamRoleBlock, "Should create IAM Role block")
        assertEquals(BlockType.SECURITY, iamRoleBlock.type)

        // Verify that the assume_role_policy property should be identified as JSON type
        val assumeRolePolicyProperty = iamRoleBlock.properties["assume_role_policy"]
        assertNotNull(assumeRolePolicyProperty, "IAM Role block should have assume_role_policy property")

        // This checks if the property is identified as a JSON property
        // This will fail until the TerraformParser is updated to identify JSON properties
        val iamRoleProperties = TerraformProperties.getPropertiesForBlock(iamRoleBlock)
        val assumeRolePolicyTfProperty = iamRoleProperties.find { it.name == "assume_role_policy" }
        assertNotNull(assumeRolePolicyTfProperty, "Should find assume_role_policy in the schema properties")
        assertEquals(
            PropertyType.JSON, assumeRolePolicyTfProperty.type,
            "assume_role_policy should be identified as PropertyType.JSON"
        )

        // Verify IAM policy is converted to SECURITY block
        val iamPolicyBlock = blocks.find { it.resourceType == ResourceType.IAM_POLICY }
        assertNotNull(iamPolicyBlock, "Should create IAM Policy block")
        assertEquals(BlockType.SECURITY, iamPolicyBlock.type)

        // Verify that the policy property should be identified as JSON type
        val policyProperty = iamPolicyBlock.properties["policy"]
        assertNotNull(policyProperty, "IAM Policy block should have policy property")

        // Check that the policy property is correctly identified as JSON
        val iamPolicyProperties = TerraformProperties.getPropertiesForBlock(iamPolicyBlock)
        val policyTfProperty = iamPolicyProperties.find { it.name == "policy" }
        assertNotNull(policyTfProperty, "Should find policy in the schema properties")
        assertEquals(
            PropertyType.JSON, policyTfProperty.type,
            "policy should be identified as PropertyType.JSON"
        )
    }
} 