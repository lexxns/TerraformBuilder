package terraformbuilder.components

import androidx.compose.ui.geometry.Offset
import org.junit.Test
import terraformbuilder.ResourceType
import terraformbuilder.components.block.Block
import terraformbuilder.components.block.BlockType
import terraformbuilder.components.block.ConnectionPointType
import kotlin.test.assertEquals

class BlockViewTest {

    @Test
    fun testBlockProperties() {
        // Create a test block
        val testBlock = Block(
            id = "test-block-1",
            type = BlockType.EC2,
            content = "Test Lambda",
            resourceType = ResourceType.LAMBDA_FUNCTION,
            _position = Offset(100f, 100f)
        ).apply {
            setProperty("function_name", "my-test-function")
            setProperty("runtime", "nodejs18.x")
        }

        // Verify the block properties
        assertEquals("Test Lambda", testBlock.content)
        assertEquals(BlockType.EC2, testBlock.type)
        assertEquals(ResourceType.LAMBDA_FUNCTION, testBlock.resourceType)
        assertEquals("my-test-function", testBlock.getProperty("function_name"))
        assertEquals("nodejs18.x", testBlock.getProperty("runtime"))
    }

    @Test
    fun testBlockPositioning() {
        // Create a test block
        val testBlock = Block(
            id = "test-block-2",
            type = BlockType.DYNAMODB,
            content = "Test Database",
            resourceType = ResourceType.DYNAMODB_TABLE,
            _position = Offset(100f, 100f)
        )

        // Verify initial position
        assertEquals(Offset(100f, 100f), testBlock.position)

        // Update position
        testBlock.position = Offset(200f, 300f)

        // Verify updated position
        assertEquals(Offset(200f, 300f), testBlock.position)

        // Verify that connection points were updated
        val inputPoint = testBlock.getConnectionPointPosition(ConnectionPointType.INPUT)
        val outputPoint = testBlock.getConnectionPointPosition(ConnectionPointType.OUTPUT)

        // Verify connection points are relative to the new block position
        assertEquals(200f - 6f, inputPoint.x) // Input point is 6 units to the left
        assertEquals(300f + testBlock.size.y / 2f, inputPoint.y) // Vertically centered
    }

    @Test
    fun testBlockRenaming() {
        // Create a test block
        val testBlock = Block(
            id = "test-block-3",
            type = BlockType.VPC,
            content = "Test VPC",
            resourceType = ResourceType.VPC,
            _position = Offset(100f, 100f)
        )

        // Test initial content
        assertEquals("Test VPC", testBlock.content)

        // Update content (rename block)
        testBlock.content = "Renamed VPC"

        // Verify content was updated
        assertEquals("Renamed VPC", testBlock.content)
    }
} 