package terraformbuilder.integration

import org.junit.Test
import terraformbuilder.ResourceType
import terraformbuilder.components.block.BlockState
import terraformbuilder.components.block.BlockType
import terraformbuilder.github.GithubRepoInfo
import terraformbuilder.github.MockGithubService
import terraformbuilder.terraform.TerraformParser
import terraformbuilder.terraform.VariableState
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubToTerraformTest {

    @Test
    fun `test load from GitHub and create block state and variables`() {
        // 1. Create mock repo info for aws_lambda_api
        val repoInfo = GithubRepoInfo(
            owner = "lexxns",
            repo = "terraform-examples",
            path = "aws/aws_lambda_api",
            branch = "master"
        )

        // 2. Use mock service to load Terraform files
        val mockService = MockGithubService()
        val files = mockService.loadTerraformFiles(repoInfo)

        // Check files were loaded
        assertTrue(files.isNotEmpty(), "Should load Terraform files")

        // 3. Parse the files
        val parser = TerraformParser()
        val allResources = mutableListOf<terraformbuilder.terraform.TerraformResource>()
        val variableState = VariableState()

        // Process all files
        files.forEach { fileContent ->
            val parseResult = parser.parse(fileContent)
            allResources.addAll(parseResult.resources)
            parseResult.variables.forEach { variable ->
                variableState.addVariable(variable)
            }
        }

        // 4. Verify resources were parsed correctly
        assertTrue(allResources.isNotEmpty(), "Should parse resources from files")
        assertTrue(variableState.variables.isNotEmpty(), "Should find variables in files")

        // Verify specific resources were found
        val lambdaResource = allResources.find { it.type == "aws_lambda_function" }
        assertNotNull(lambdaResource, "Should find Lambda function resource")
        assertEquals("local_zipfile", lambdaResource.name)

        val apiGatewayResource = allResources.find { it.type == "aws_api_gateway_rest_api" }
        assertNotNull(apiGatewayResource, "Should find API Gateway resource")

        // Verify variables
        val apiDomainVar = variableState.variables.find { it.name == "api_domain" }
        assertNotNull(apiDomainVar, "Should have api_domain variable")

        // 5. Convert to visual blocks
        val blockState = BlockState()
        val blocks = parser.convertToBlocks(allResources)

        // Arrange blocks in a grid (just for testing)
        blocks.forEachIndexed { index, block ->
            val row = index / 3
            val col = index % 3
            block.position = androidx.compose.ui.geometry.Offset(
                100f + col * 200f,
                100f + row * 150f
            )
            blockState.addBlock(block)
        }

        // 6. Verify blocks were created with correct types
        assertTrue(blockState.allBlocks.isNotEmpty(), "Should create blocks from resources")

        // Check for specific block types
        val lambdaBlock = blockState.allBlocks.find { it.resourceType == ResourceType.LAMBDA_FUNCTION }
        assertNotNull(lambdaBlock, "Should create Lambda function block")
        assertEquals(BlockType.LAMBDA, lambdaBlock.type)

        val apiGatewayBlock = blockState.allBlocks.find { it.resourceType == ResourceType.API_GATEWAY_REST_API }
        assertNotNull(apiGatewayBlock, "Should create API Gateway block")
        assertEquals(BlockType.INTEGRATION, apiGatewayBlock.type)

        // 7. Check that all blocks have the correct properties from the original Terraform files
        val deploymentBlock = blockState.allBlocks.find {
            it.resourceType == ResourceType.API_GATEWAY_DEPLOYMENT
        }
        assertNotNull(deploymentBlock, "Should create API Gateway Deployment block")
        assertTrue(
            deploymentBlock.properties.containsKey("rest_api_id"),
            "Deployment block should have rest_api_id property"
        )
        assertTrue(
            deploymentBlock.properties.containsKey("depends_on"),
            "Deployment block should have depends_on property"
        )

        // Check for the stage block
        val stageBlock = blockState.allBlocks.find {
            it.resourceType == ResourceType.API_GATEWAY_STAGE
        }
        assertNotNull(stageBlock, "Should create API Gateway Stage block")
        assertTrue(
            stageBlock.properties.containsKey("stage_name"),
            "Stage block should have stage_name property"
        )
        assertTrue(
            stageBlock.properties.containsKey("rest_api_id"),
            "Stage block should have rest_api_id property"
        )
        assertTrue(
            stageBlock.properties.containsKey("deployment_id"),
            "Stage block should have deployment_id property"
        )
    }
} 