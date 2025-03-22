import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.*

private fun createLibraryBlock(type: BlockType, content: String): Block {
    return createBlockWithConnections(
        id = UUID.randomUUID().toString(),
        type = type,
        content = content
    )
}

@Composable
@Preview
fun app() {
    val blockState = remember { BlockState() }
    
    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Block Library Panel
            blockLibraryPanel(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(250.dp)
                    .background(Color(0xFFF5F5F5)),
                onBlockSelected = { block ->
                    blockState.addBlock(createBlockWithConnections(
                        id = UUID.randomUUID().toString(),
                        type = block.type,
                        content = block.content
                    ))
                }
            )
            
            // Workspace Area
            workspaceArea(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                blockState = blockState
            )
        }
    }
}

@Composable
fun blockLibraryPanel(
    modifier: Modifier = Modifier,
    onBlockSelected: (Block) -> Unit
) {
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("AWS Infrastructure", style = MaterialTheme.typography.h6)
        }
        
        // Compute Resources
        item {
            collapsibleCategory(
                name = "Compute",
                isExpanded = expandedCategories.contains("Compute"),
                onToggle = { 
                    expandedCategories = if (expandedCategories.contains("Compute")) {
                        expandedCategories - "Compute"
                    } else {
                        expandedCategories + "Compute"
                    }
                },
                blocks = listOf(
                    createLibraryBlock(BlockType.COMPUTE, "Lambda Function"),
                    createLibraryBlock(BlockType.COMPUTE, "EC2 Instance"),
                    createLibraryBlock(BlockType.COMPUTE, "ECS Cluster"),
                    createLibraryBlock(BlockType.COMPUTE, "ECS Service")
                ),
                onBlockSelected = onBlockSelected
            )
        }
        
        // Database Resources
        item {
            collapsibleCategory(
                name = "Database",
                isExpanded = expandedCategories.contains("Database"),
                onToggle = { 
                    expandedCategories = if (expandedCategories.contains("Database")) {
                        expandedCategories - "Database"
                    } else {
                        expandedCategories + "Database"
                    }
                },
                blocks = listOf(
                    createLibraryBlock(BlockType.DATABASE, "DynamoDB Table"),
                    createLibraryBlock(BlockType.DATABASE, "RDS Instance"),
                    createLibraryBlock(BlockType.DATABASE, "S3 Bucket"),
                    createLibraryBlock(BlockType.DATABASE, "ElastiCache")
                ),
                onBlockSelected = onBlockSelected
            )
        }
        
        // Networking Resources
        item {
            collapsibleCategory(
                name = "Networking",
                isExpanded = expandedCategories.contains("Networking"),
                onToggle = { 
                    expandedCategories = if (expandedCategories.contains("Networking")) {
                        expandedCategories - "Networking"
                    } else {
                        expandedCategories + "Networking"
                    }
                },
                blocks = listOf(
                    createLibraryBlock(BlockType.NETWORKING, "VPC"),
                    createLibraryBlock(BlockType.NETWORKING, "Subnet"),
                    createLibraryBlock(BlockType.NETWORKING, "Security Group"),
                    createLibraryBlock(BlockType.NETWORKING, "Route Table")
                ),
                onBlockSelected = onBlockSelected
            )
        }
        
        // Security Resources
        item {
            collapsibleCategory(
                name = "Security",
                isExpanded = expandedCategories.contains("Security"),
                onToggle = { 
                    expandedCategories = if (expandedCategories.contains("Security")) {
                        expandedCategories - "Security"
                    } else {
                        expandedCategories + "Security"
                    }
                },
                blocks = listOf(
                    createLibraryBlock(BlockType.SECURITY, "IAM Role"),
                    createLibraryBlock(BlockType.SECURITY, "IAM Policy"),
                    createLibraryBlock(BlockType.SECURITY, "KMS Key"),
                    createLibraryBlock(BlockType.SECURITY, "Secrets Manager")
                ),
                onBlockSelected = onBlockSelected
            )
        }
        
        // Integration Resources
        item {
            collapsibleCategory(
                name = "Integration",
                isExpanded = expandedCategories.contains("Integration"),
                onToggle = { 
                    expandedCategories = if (expandedCategories.contains("Integration")) {
                        expandedCategories - "Integration"
                    } else {
                        expandedCategories + "Integration"
                    }
                },
                blocks = listOf(
                    createLibraryBlock(BlockType.INTEGRATION, "API Gateway"),
                    createLibraryBlock(BlockType.INTEGRATION, "SQS Queue"),
                    createLibraryBlock(BlockType.INTEGRATION, "SNS Topic"),
                    createLibraryBlock(BlockType.INTEGRATION, "EventBridge Rule")
                ),
                onBlockSelected = onBlockSelected
            )
        }
        
        // Monitoring Resources
        item {
            collapsibleCategory(
                name = "Monitoring",
                isExpanded = expandedCategories.contains("Monitoring"),
                onToggle = { 
                    expandedCategories = if (expandedCategories.contains("Monitoring")) {
                        expandedCategories - "Monitoring"
                    } else {
                        expandedCategories + "Monitoring"
                    }
                },
                blocks = listOf(
                    createLibraryBlock(BlockType.MONITORING, "CloudWatch Log Group"),
                    createLibraryBlock(BlockType.MONITORING, "CloudWatch Alarm"),
                    createLibraryBlock(BlockType.MONITORING, "X-Ray Trace"),
                    createLibraryBlock(BlockType.MONITORING, "CloudWatch Dashboard")
                ),
                onBlockSelected = onBlockSelected
            )
        }
    }
}

@Composable
fun collapsibleCategory(
    name: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    blocks: List<Block>,
    onBlockSelected: (Block) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
        }
        
        if (isExpanded) {
            blocks.forEach { block ->
                blockItem(
                    block = block,
                    onClick = { onBlockSelected(block) }
                )
            }
        }
    }
}

@Composable
fun workspaceArea(
    modifier: Modifier = Modifier,
    blockState: BlockState
) {
    var activeConnection by remember { mutableStateOf<Connection?>(null) }
    val selectedConnectionPoint by remember { mutableStateOf<ConnectionPoint?>(null) }
    var draggingConnectionPoint by remember { mutableStateOf<ConnectionPoint?>(null) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8))
    ) {
        // Draw existing connections
        blockState.connections.forEach { connection ->
            val fromBlock = blockState.blocks.find { it.id == connection.fromBlockId }
            val toBlock = blockState.blocks.find { it.id == connection.toBlockId }
            if (fromBlock != null && toBlock != null) {
                connectionLine(
                    connection = connection,
                    fromBlock = fromBlock,
                    toBlock = toBlock,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Draw blocks
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(blockState.blocks) { block ->
                draggableBlock(
                    block = block,
                    onDragEnd = { newPosition ->
                        blockState.updateBlockPosition(block.id, newPosition)
                    },
                    onRename = { newContent ->
                        blockState.updateBlockContent(block.id, newContent)
                    },
                    onConnectionPointSelected = { point ->
                        if (draggingConnectionPoint == null) {
                            draggingConnectionPoint = point
                            activeConnection = Connection(
                                id = UUID.randomUUID().toString(),
                                fromBlockId = block.id,
                                toBlockId = "",
                                fromPointId = point.id,
                                toPointId = ""
                            )
                        } else {
                            // Complete the connection
                            if (point.type != draggingConnectionPoint!!.type) {
                                activeConnection = activeConnection?.copy(
                                    toBlockId = block.id,
                                    toPointId = point.id
                                )
                                activeConnection?.let { blockState.addConnection(it) }
                            }
                            draggingConnectionPoint = null
                            activeConnection = null
                        }
                    }
                )
            }
        }
        
        // Draw active connection line if dragging
        activeConnection?.let { connection ->
            val fromBlock = blockState.blocks.find { it.id == connection.fromBlockId }
            val fromPoint = fromBlock?.connectionPoints?.find { it.id == connection.fromPointId }
            selectedConnectionPoint?.let { toPoint ->
                connectionLine(
                    connection = connection,
                    fromBlock = fromBlock!!,
                    toBlock = Block(
                        id = "temp",
                        type = BlockType.COMPUTE,
                        position = toPoint.position,
                        content = ""
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Terraform Builder",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        app()
    }
}
