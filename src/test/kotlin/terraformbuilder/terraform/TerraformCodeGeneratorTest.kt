package terraformbuilder.terraform

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import terraformbuilder.ResourceType
import terraformbuilder.components.Block
import terraformbuilder.components.BlockState
import terraformbuilder.components.BlockType
import terraformbuilder.components.Connection
import terraformbuilder.github.GithubRepoInfo
import terraformbuilder.github.MockGithubService
import terraformbuilder.terraformbuilder.TerraformCodeGenerator
import java.io.File
import java.nio.file.Paths
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerraformCodeGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var blocks: List<Block> = listOf()
    private val variableState = VariableState()
    private val resourcePath = "terraform-examples/aws/aws_lambda_api"
    private val inputDir = Paths.get(File(javaClass.classLoader.getResource(resourcePath)?.file).toURI())

    @BeforeTest
    fun beforeTest() {
        val repoInfo = GithubRepoInfo(
            owner = "lexxns",
            repo = "terraform-examples",
            path = "aws/aws_lambda_api",
            branch = "master"
        )

        val mockService = MockGithubService()
        val files = mockService.loadTerraformFiles(repoInfo)

        val parser = TerraformParser()
        val allResources = mutableListOf<TerraformResource>()

        files.forEach { fileContent ->
            val parseResult = parser.parse(fileContent)
            allResources.addAll(parseResult.resources)
            parseResult.variables.forEach { variable ->
                variableState.addVariable(variable)
            }
        }

        val blockState = BlockState()
        blocks = parser.convertToBlocks(allResources)

        blocks.forEachIndexed { index, block ->
            val row = index / 3
            val col = index % 3
            block.position = Offset(
                100f + col * 200f,
                100f + row * 150f
            )
            blockState.addBlock(block)
        }
    }

    @Test
    fun `test generate terraform code from blocks and variables`() = runBlocking {
        // Create test blocks
        val vpcBlock = Block(
            id = "vpc-1",
            type = BlockType.VPC,
            content = "main-vpc",
            resourceType = ResourceType.VPC,
            description = "Main VPC",
            _position = Offset(100f, 100f)
        ).apply {
            setProperty("cidr_block", "10.0.0.0/16")
            setProperty("enable_dns_support", "true")
            setProperty("tags", "{Name = main-vpc, Environment = test}")
        }

        val subnetBlock = Block(
            id = "subnet-1",
            type = BlockType.VPC,
            content = "public-subnet",
            resourceType = ResourceType.SUBNET,
            description = "Public Subnet",
            _position = Offset(300f, 100f)
        ).apply {
            setProperty("cidr_block", "10.0.1.0/24")
            setProperty("availability_zone", "us-west-2a")
            setProperty("map_public_ip_on_launch", "true")
        }

        val lambdaBlock = Block(
            id = "lambda-1",
            type = BlockType.LAMBDA,
            content = "example-function",
            resourceType = ResourceType.LAMBDA_FUNCTION,
            description = "Example Lambda Function",
            _position = Offset(700f, 100f)
        ).apply {
            setProperty("function_name", "example-\${var.environment}-function")
            setProperty("handler", "index.handler")
            setProperty("runtime", "nodejs18.x")
        }

        // Create connections between blocks
        val connection1 = Connection(
            sourceBlock = vpcBlock,
            targetBlock = subnetBlock
        )

        val connection2 = Connection(
            sourceBlock = subnetBlock,
            targetBlock = lambdaBlock
        )

        // Create variables
        val amiVariable = TerraformVariable(
            name = "my_ami_id",
            type = VariableType.STRING,
            description = "AMI ID for EC2 instance",
            defaultValue = "ami-12345678"
        )

        val environmentVariable = TerraformVariable(
            name = "environment",
            type = VariableType.STRING,
            description = "Deployment environment",
            defaultValue = "test"
        )

        // Output directory
        val outputDir = tempFolder.newFolder("terraform-output")

        // Generate Terraform code
        val generator = TerraformCodeGenerator()
        generator.generateCode(
            blocks = listOf(vpcBlock, subnetBlock, lambdaBlock),
            connections = listOf(connection1, connection2),
            variables = listOf(amiVariable, environmentVariable),
            outputDir = outputDir
        )

        // Verify files were created
        val mainFile = File(outputDir, "main.tf")
        val variablesFile = File(outputDir, "variables.tf")
        val outputsFile = File(outputDir, "outputs.tf")
        val providerFile = File(outputDir, "provider.tf")

        assertTrue(mainFile.exists(), "main.tf should exist")
        assertTrue(variablesFile.exists(), "variables.tf should exist")
        assertTrue(outputsFile.exists(), "outputs.tf should exist")
        assertTrue(providerFile.exists(), "provider.tf should exist")

        // Verify content
        val mainContent = mainFile.readText()

        // Check that resource declarations are present
        assertTrue(
            mainContent.contains("resource \"aws_vpc\" \"main_vpc\""),
            "Should contain VPC resource"
        )
        assertTrue(
            mainContent.contains("resource \"aws_subnet\" \"public_subnet\""),
            "Should contain subnet resource"
        )
        assertTrue(
            mainContent.contains("resource \"aws_lambda_function\" \"example_function\""),
            "Should contain Lambda function resource"
        )

        // Check reference from subnet to VPC
        assertTrue(
            mainContent.contains("vpc_id = aws_vpc.main_vpc.id"),
            "Subnet should reference VPC ID"
        )

        // Check terraform interpolation in the function name
        assertTrue(
            mainContent.contains("function_name = \"example-\${var.environment}-function\""),
            "Lambda should preserve Terraform interpolation syntax"
        )

        val variablesContent = variablesFile.readText()

        // Check variable references
        assertTrue(
            variablesContent.contains("variable \"environment\""),
            "Should contain environment variable"
        )
        assertTrue(
            variablesContent.contains("variable \"my_ami_id\""),
            "Should contain AMI variable"
        )
        assertTrue(
            variablesContent.contains("default     = \"ami-12345678\""),
            "Should contain AMI default value"
        )

        // Check outputs file
        val outputsContent = outputsFile.readText()
        assertTrue(
            outputsContent.contains("output \"example_function_function_name\""),
            "Should output Lambda function name"
        )
    }

    @Test
    fun `test generate terrafrom from github creates the same terraform`() {
        val outputDir = tempFolder.newFolder("terraform-output")

        // Act - convert to terraform
        val generator = TerraformCodeGenerator()
        generator.generateCode(
            blocks = blocks,
            connections = listOf(),
            variables = variableState.variables,
            outputDir = outputDir
        )

        // Assert - check files match
        val filesInInput = File(inputDir.toString())
            .walkTopDown()
            .filter { it.isFile && it.extension == "tf" }
            .toList()

        var mismatches = 0

        filesInInput.forEach { file ->
            // Calculate the relative path from the base directory
            val relativePath = file.absolutePath.substringAfter(inputDir.toString())

            // Construct the path to the corresponding file in folder B
            val outFile = File(outputDir, relativePath)

            // Check if the file exists in folder B
            if (!outFile.exists()) {
                println("File $relativePath exists in folder A but not in folder B")
                mismatches++
                return@forEach
            }

            // Read the contents of both files
            val contentA = file.readText()
            val contentB = outFile.readText()

            // Compare the contents
            if (contentA != contentB) {
                println("Content mismatch for file $relativePath")
                mismatches++
            }
        }

        // Assert that there were no mismatches
        assertEquals(0, mismatches, "Found $mismatches files that don't match between folder A and folder B")
    }

    @Test
    fun `test generate terrafrom from github creates the same variables`() {
        val outputDir = tempFolder.newFolder("terraform-output")

        // Act - convert to terraform
        val generator = TerraformCodeGenerator()
        generator.generateCode(
            blocks = blocks,
            connections = listOf(),
            variables = variableState.variables,
            outputDir = outputDir
        )

        // Assert - check files match
        val variablesFile = File(inputDir.toFile(), "variables.tf")
        val outFile = File(outputDir, "variables.tf")

        // Read the contents of both files
        val inVars = variablesFile.readText()
        val outVars = outFile.readText()

        assertEquals(inVars, outVars)
    }
}