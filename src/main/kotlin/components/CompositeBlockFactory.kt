package terraformbuilder.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import terraformbuilder.ResourceType
import java.util.*

object CompositeBlockFactory {
    // Create a REST API template
    fun createRestApi(name: String, position: Offset): CompositeBlock {

        // Create child blocks
        val (apiGateway, apiGatewayResource) = apiGatewayResources(name)

        return CompositeBlock(
            name = name,
            description = "REST API with Gateway, Lambda, and IAM roles",
            _position = position,
            iconCode = "Api", // Material icon name
            color = Color(0xFF4285F4) // Blue
        ).apply {
            // high-level properties
            setProperty("api_name", name)
            setProperty("stage_name", "v1")

            // Child blocks
            addChild(apiGateway)
            addChild(apiGatewayResource)
        }
    }

    // VPC Network template
    fun createVpcNetwork(name: String, position: Offset): CompositeBlock {
        return CompositeBlock(
            name = name,
            description = "VPC network with subnets, route tables, and NAT gateways",
            _position = position,
            iconCode = "Cloud", // Material icon name
            color = Color(0xFF34A853) // Green
        ).apply {
            // TODO
        }
    }

    // Serverless Backend template
    fun createServerlessBackend(name: String, position: Offset): CompositeBlock {
        return CompositeBlock(
            name = name,
            description = "Serverless backend with Lambda, DynamoDB, and API Gateway",
            _position = position,
            iconCode = "Code", // Material icon name
            color = Color(0xFFEA4335) // Red
        ).apply {
            // TODO
        }
    }

    // Database Cluster template
    fun createDatabaseCluster(name: String, position: Offset): CompositeBlock {
        return CompositeBlock(
            name = name,
            description = "Database cluster with RDS instances and security groups",
            _position = position,
            iconCode = "Storage", // Material icon name
            color = Color(0xFFFBBC05) // Yellow
        ).apply {
            // TODO
        }
    }

    // Empty custom group
    fun createCustomGroup(name: String, position: Offset): CompositeBlock {
        return CompositeBlock(
            name = name,
            description = "Custom group of resources",
            _position = position
        )
    }

    private fun apiGatewayResources(name: String): Pair<Block, Block> {
        val apiGateway = Block(
            id = UUID.randomUUID().toString(),
            type = BlockType.API_GATEWAY,
            content = "$name-api",
            resourceType = ResourceType.API_GATEWAY_REST_API,
            _position = Offset(50f, 50f)
        ).apply {
            setProperty("name", "$name-api")
        }

        val apiGatewayResource = Block(
            id = UUID.randomUUID().toString(),
            type = BlockType.API_GATEWAY,
            content = "$name-api-resource",
            resourceType = ResourceType.API_GATEWAY_RESOURCE,
            _position = Offset(70f, 70f)
        ).apply {
            setProperty("name", "$name-api-resource")
        }
        return Pair(apiGateway, apiGatewayResource)
    }
}