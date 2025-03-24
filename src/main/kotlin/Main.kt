package terraformbuilder

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.util.*

// Library block creation helper
private fun createLibraryBlock(type: BlockType, content: String, resourceType: ResourceType): Block {
    return createBlock(
        id = UUID.randomUUID().toString(),
        type = type,
        content = content,
        resourceType = resourceType
    )
}

// Generate default names for resources
private fun generateDefaultName(resourceType: ResourceType): String {
    val randomId = UUID.randomUUID().toString().substring(0, 4)

    val prefix = resourceType.displayName.lowercase(Locale.getDefault()).replace(" ", "-")
    return "$prefix-$randomId"
}

@Composable
@Preview
fun app() {
    var selectedBlock by remember { mutableStateOf<Block?>(null) }
    var mousePosition by remember { mutableStateOf(Offset.Zero) }
    var hoverPosition by remember { mutableStateOf(Offset.Zero) }
    var hoverDpPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density

    val blockState = remember { BlockState() }
    
    // Add sample blocks on first launch
    LaunchedEffect(Unit) {
        // Add some sample blocks with initial properties
        blockState.addBlock(
            createBlock(
                id = "block1",
                type = BlockType.COMPUTE,
                content = "lambda-demo",
                resourceType = ResourceType.LAMBDA_FUNCTION
            ).apply { 
                position = Offset(100f, 100f)
                setProperty("function_name", "my-lambda-function")
                setProperty("runtime", "nodejs18.x")
                setProperty("handler", "index.handler")
            }
        )
        
        blockState.addBlock(
            createBlock(
                id = "block2",
                type = BlockType.DATABASE,
                content = "bucket-storage",
                resourceType = ResourceType.S3_BUCKET
            ).apply { 
                position = Offset(400f, 200f) 
                setProperty("bucket", "my-terraform-bucket")
                setProperty("versioning_enabled", "true")
            }
        )
        
        println("App: LaunchedEffect completed - blocks added")
    }
    
    // Track when the mouse button is down during a drag
    var isMouseDown by remember { mutableStateOf(false) }
    
    // When blocks start a connection, update our state
    val onConnectionDragStart: (Block, ConnectionPointType) -> Unit = { block, pointType ->
        // Start the connection
        blockState.startConnectionDrag(block, pointType)
        
        // Set the initial drag position to the connection point's position
        val connectionPoint = block.getConnectionPointPosition(pointType)
        blockState.updateDragPosition(connectionPoint)
        
        // Set the mouse as down so we know we're dragging a connection
        isMouseDown = true
        
        println("APP: Connection drag started from ${block.content}, type $pointType")
        println("APP: Initial drag position set to $connectionPoint")
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Block Library Panel
            blockLibraryPanel(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(250.dp)
                    .background(Color.LightGray)
                    .padding(8.dp),
                onBlockSelected = { block ->
                    // Create a copy with a unique ID and a default name based on the resource type
                    val resourceType = block.resourceType
                    selectedBlock = block.copy(
                        id = UUID.randomUUID().toString(),
                        content = generateDefaultName(resourceType),
                        resourceType = resourceType
                    )
                }
            )

            // Workspace Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                hoverPosition = offset
                                hoverDpPosition = offset.toDpOffset(density)
                                println("WORKSPACE: Drag started at position: $offset, dp: $hoverDpPosition")
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                hoverPosition = change.position
                                hoverDpPosition = change.position.toDpOffset(density)
                                
                                // Update connection drag position if active
                                if (blockState.dragState.isActive) {
                                    blockState.updateDragPosition(hoverDpPosition)
                                }
                            },
                            onDragEnd = {
                                println("WORKSPACE: Drag ended at position: $hoverDpPosition")
                                // When the drag ends, complete any active connection
                                if (blockState.dragState.isActive) {
                                    blockState.endConnectionDrag(hoverDpPosition)
                                }
                                isMouseDown = false
                            }
                        )
                    }
                    // Add separate pointer input for mouse moves without dragging
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.first().position
                                
                                // Update hover position on any mouse movement
                                hoverPosition = position
                                hoverDpPosition = position.toDpOffset(density)
                                
                                // If we're dragging a connection, update its position
                                if (blockState.dragState.isActive && isMouseDown) {
                                    blockState.updateDragPosition(hoverDpPosition)
                                }
                            }
                        }
                    }
            ) {
                workspaceArea(
                    modifier = Modifier.fillMaxSize(),
                    blockState = blockState,
                    hoverPosition = hoverPosition,
                    hoverDpPosition = hoverDpPosition,
                    onConnectionDragStart = onConnectionDragStart
                )

                // Add new block when selected
                LaunchedEffect(selectedBlock) {
                    selectedBlock?.let { block ->
                        blockState.addBlock(block)
                        selectedBlock = null
                    }
                }
            }
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
                    createLibraryBlock(BlockType.COMPUTE, "Lambda Function", ResourceType.LAMBDA_FUNCTION),
                    createLibraryBlock(BlockType.COMPUTE, "EC2 Instance", ResourceType.EC2_INSTANCE),
                    createLibraryBlock(BlockType.COMPUTE, "ECS Cluster", ResourceType.ECS_CLUSTER),
                    createLibraryBlock(BlockType.COMPUTE, "ECS Service", ResourceType.ECS_SERVICE)
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
                    createLibraryBlock(BlockType.DATABASE, "DynamoDB Table", ResourceType.DYNAMODB_TABLE),
                    createLibraryBlock(BlockType.DATABASE, "RDS Instance", ResourceType.RDS_INSTANCE),
                    createLibraryBlock(BlockType.DATABASE, "S3 Bucket", ResourceType.S3_BUCKET),
                    createLibraryBlock(BlockType.DATABASE, "ElastiCache", ResourceType.ELASTICACHE)
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
                    createLibraryBlock(BlockType.NETWORKING, "VPC", ResourceType.VPC),
                    createLibraryBlock(BlockType.NETWORKING, "Subnet", ResourceType.SUBNET),
                    createLibraryBlock(BlockType.NETWORKING, "Security Group", ResourceType.SECURITY_GROUP),
                    createLibraryBlock(BlockType.NETWORKING, "Route Table", ResourceType.ROUTE_TABLE)
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
                    createLibraryBlock(BlockType.SECURITY, "IAM Role", ResourceType.IAM_ROLE),
                    createLibraryBlock(BlockType.SECURITY, "IAM Policy", ResourceType.IAM_POLICY),
                    createLibraryBlock(BlockType.SECURITY, "KMS Key", ResourceType.KMS_KEY),
                    createLibraryBlock(BlockType.SECURITY, "Secrets Manager", ResourceType.SECRETS_MANAGER)
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
                    createLibraryBlock(BlockType.INTEGRATION, "API Gateway", ResourceType.API_GATEWAY),
                    createLibraryBlock(BlockType.INTEGRATION, "SQS Queue", ResourceType.SQS_QUEUE),
                    createLibraryBlock(BlockType.INTEGRATION, "SNS Topic", ResourceType.SNS_TOPIC),
                    createLibraryBlock(BlockType.INTEGRATION, "EventBridge Rule", ResourceType.EVENTBRIDGE_RULE)
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
                    createLibraryBlock(BlockType.MONITORING, "CloudWatch Log Group", ResourceType.CLOUDWATCH_LOG_GROUP),
                    createLibraryBlock(BlockType.MONITORING, "CloudWatch Alarm", ResourceType.CLOUDWATCH_ALARM),
                    createLibraryBlock(BlockType.MONITORING, "X-Ray Trace", ResourceType.XRAY_TRACE),
                    createLibraryBlock(BlockType.MONITORING, "CloudWatch Dashboard", ResourceType.CLOUDWATCH_DASHBOARD)
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
            for (block in blocks) {
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
    blockState: BlockState,
    hoverPosition: Offset,
    hoverDpPosition: Offset,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit
) {
    // State to track clicked positions for debugging
    var clickedPosition by remember { mutableStateOf<Offset?>(null) }
    var clickedDpPosition by remember { mutableStateOf<Offset?>(null) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current.density

    // Collect connection points for debugging
    val connectionPoints = remember(blockState.blocks) {
        blockState.blocks.flatMap { block ->
            listOf(
                Triple(block.id, ConnectionPointType.INPUT, block.inputPosition),
                Triple(block.id, ConnectionPointType.OUTPUT, block.outputPosition)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8))
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    // Record clicked position for debugging
                    clickedPosition = position
                    clickedDpPosition = position.toDpOffset(density)

                    // Check if we clicked on a block for property editing
                    val dpPos = position.toDpOffset(density)
                    val clickedBlockId = blockState.blocks.find { block ->
                        // Check if the click (in dp) is within the block bounds (also in dp)
                        val blockLeft = block.position.x
                        val blockRight = blockLeft + block.size.x
                        val blockTop = block.position.y
                        val blockBottom = blockTop + block.size.y

                        dpPos.x in blockLeft..blockRight && dpPos.y in blockTop..blockBottom
                    }?.id
                    
                    // Update selected block (null if clicked on empty space)
                    selectedBlockId = clickedBlockId
                    
                    println("Workspace click: ${if (clickedBlockId != null) "Selected block $clickedBlockId" else "Deselected block"}")
                }
            }
    ) {
        /* Uncomment for debugging
        // Draw debugging grid
        DebugGrid(
            clickedPosition = clickedPosition,
            clickedDpPosition = clickedDpPosition,
            hoverPosition = hoverPosition,
            hoverDpPosition = hoverDpPosition,
            connectionPoints = connectionPoints,
            blocks = blockState.blocks,
            modifier = Modifier.fillMaxSize()
        )
        */

        // Draw connections between blocks
        connectionCanvas(
            connections = blockState.connections,
            dragState = blockState.dragState,
            blocks = blockState.blocks,
            modifier = Modifier.fillMaxSize()
        )

        // Draw blocks
        for (block in blockState.blocks) {
            blockView(
                block = block,
                onDragEnd = { newPosition ->
                    blockState.updateBlockPosition(block.id, newPosition)
                },
                onRename = { newContent ->
                    blockState.updateBlockContent(block.id, newContent)
                },
                onConnectionDragStart = onConnectionDragStart,
                onUpdateBlockSize = { blockId, size ->
                    blockState.updateBlockSize(blockId, size)
                },
                onBlockSelected = { blockId -> 
                    selectedBlockId = blockId
                    println("Block selected through click: $blockId")
                }
            )
        }

        // Display property editor for selected block
        selectedBlockId?.let { id ->
            val block = blockState.blocks.find { it.id == id }
            block?.let {
                // Property editor panel
                propertyEditorPanel(
                    block = block,
                    onPropertyChange = { propertyName, propertyValue ->
                        blockState.updateBlockProperty(id, propertyName, propertyValue)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
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