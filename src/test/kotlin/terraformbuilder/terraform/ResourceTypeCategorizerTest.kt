package terraformbuilder.terraform

import org.junit.Assert.assertEquals
import org.junit.Test
import terraformbuilder.ResourceType
import terraformbuilder.components.BlockType

class ResourceTypeCategorizerTest {
    private val categorizer = ResourceTypeCategorizer()

    @Test
    fun testComputeResources() {
        assertEquals(BlockType.LAMBDA, categorizer.determineBlockType(ResourceType.LAMBDA_FUNCTION))
        assertEquals(BlockType.EC2, categorizer.determineBlockType(ResourceType.EC2_HOST))
        assertEquals(BlockType.EC2, categorizer.determineBlockType(ResourceType.ECS_CLUSTER))
        assertEquals(BlockType.ECS, categorizer.determineBlockType(ResourceType.ECS_SERVICE))
    }

    @Test
    fun testDatabaseResources() {
        assertEquals(BlockType.DYNAMODB, categorizer.determineBlockType(ResourceType.DYNAMODB_TABLE))
        assertEquals(BlockType.RDS, categorizer.determineBlockType(ResourceType.RDS_CLUSTER))
        assertEquals(BlockType.STORAGE, categorizer.determineBlockType(ResourceType.S3_BUCKET))
        assertEquals(BlockType.STORAGE, categorizer.determineBlockType(ResourceType.ELASTICACHE_CLUSTER))
    }

    @Test
    fun testNetworkingResources() {
        assertEquals(BlockType.VPC, categorizer.determineBlockType(ResourceType.VPC))
        assertEquals(BlockType.INTEGRATION, categorizer.determineBlockType(ResourceType.SUBNET))
        assertEquals(BlockType.INTEGRATION, categorizer.determineBlockType(ResourceType.ROUTE_TABLE))
    }

    @Test
    fun testSecurityResources() {
        assertEquals(BlockType.SECURITY, categorizer.determineBlockType(ResourceType.IAM_ROLE))
        assertEquals(BlockType.SECURITY, categorizer.determineBlockType(ResourceType.IAM_POLICY))
        assertEquals(BlockType.SECURITY, categorizer.determineBlockType(ResourceType.KMS_KEY))
        assertEquals(BlockType.SECURITY, categorizer.determineBlockType(ResourceType.SECRETSMANAGER_SECRET))
        assertEquals(BlockType.SECURITY, categorizer.determineBlockType(ResourceType.SECURITY_GROUP))

    }

    @Test
    fun testMonitoringResources() {
        assertEquals(BlockType.MONITORING, categorizer.determineBlockType(ResourceType.CLOUDWATCH_LOG_GROUP))
        assertEquals(BlockType.MONITORING, categorizer.determineBlockType(ResourceType.CLOUDWATCH_METRIC_ALARM))
        assertEquals(BlockType.MONITORING, categorizer.determineBlockType(ResourceType.XRAY_GROUP))
        assertEquals(BlockType.MONITORING, categorizer.determineBlockType(ResourceType.CLOUDWATCH_DASHBOARD))
    }

    @Test
    fun testIntegrationResources() {
        assertEquals(BlockType.INTEGRATION, categorizer.determineBlockType(ResourceType.API_GATEWAY_REST_API))
        assertEquals(BlockType.INTEGRATION, categorizer.determineBlockType(ResourceType.SQS_QUEUE))
        assertEquals(BlockType.INTEGRATION, categorizer.determineBlockType(ResourceType.SNS_TOPIC))
        assertEquals(
            BlockType.INTEGRATION,
            categorizer.determineBlockType(ResourceType.KINESIS_FIREHOSE_DELIVERY_STREAM)
        )
    }

    @Test
    fun testManualOverrides() {
        assertEquals(BlockType.STORAGE, categorizer.determineBlockType(ResourceType.S3_BUCKET))
        assertEquals(BlockType.LOAD_BALANCER, categorizer.determineBlockType(ResourceType.ELB))
        assertEquals(BlockType.LOAD_BALANCER, categorizer.determineBlockType(ResourceType.LB))
    }

    @Test
    fun testUnknownResource() {
        assertEquals(BlockType.INTEGRATION, categorizer.determineBlockType(ResourceType.UNKNOWN))
    }
} 