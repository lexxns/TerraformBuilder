import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import java.util.*

// Library block creation helper
private fun createLibraryBlock(type: BlockType, content: String): Block {
    return createBlock(
        id = UUID.randomUUID().toString(),
        type = type,
        content = content
    )
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
        // Add some sample blocks
        blockState.addBlock(
            createBlock(
                id = "block1",
                type = BlockType.COMPUTE,
                content = "Compute Instance"
            ).apply { position = Offset(100f, 100f) }
        )
        
        blockState.addBlock(
            createBlock(
                id = "block2",
                type = BlockType.DATABASE,
                content = "Database"
            ).apply { position = Offset(400f, 200f) }
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
                    selectedBlock = block.copy(
                        id = UUID.randomUUID().toString(),
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

                    // Check if we clicked on a block for debugging
                    val clickedBlockId = blockState.blocks.find { block ->
                        // Check if the click (in dp) is within the block bounds (also in dp)
                        val blockLeft = block.position.x
                        val blockRight = blockLeft + block.size.x
                        val blockTop = block.position.y
                        val blockBottom = blockTop + block.size.y

                        // Debug output to console to help diagnose the issue
                        val dpPos = position.toDpOffset(density)
                        println("Click at $dpPos (dp), Block ${block.content} bounds: $blockLeft,$blockTop to $blockRight,$blockBottom")

                        dpPos.x in blockLeft..blockRight && dpPos.y in blockTop..blockBottom
                    }?.id

                    selectedBlockId = clickedBlockId
                }
            }
    ) {
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

        // Draw connections between blocks
        ConnectionsCanvas(
            connections = blockState.connections,
            dragState = blockState.dragState,
            blocks = blockState.blocks,
            modifier = Modifier.fillMaxSize()
        )

        // Draw blocks
        for (block in blockState.blocks) {
            BlockView(
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
                }
            )
        }

        // Display clicked position info
        clickedPosition?.let { position ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Clicked Pixels: (${position.x.toInt()}, ${position.y.toInt()})",
                    color = Color.Red
                )
                clickedDpPosition?.let { dpPos ->
                    Text(
                        text = "Clicked DP: (${dpPos.x.toInt()}, ${dpPos.y.toInt()})",
                        color = Color.Red
                    )
                }
            }
        }

        // Display hover position info
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            Text(
                text = "Hover Pixels: (${hoverPosition.x.toInt()}, ${hoverPosition.y.toInt()})",
                color = Color.Blue
            )
            Text(
                text = "Hover DP: (${hoverDpPosition.x.toInt()}, ${hoverDpPosition.y.toInt()})",
                color = Color.Blue
            )
        }

        // Display selected block debug info
        selectedBlockId?.let { id ->
            val block = blockState.blocks.find { it.id == id }
            block?.let {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .widthIn(max = 300.dp)
                ) {
                    Text("Block: ${block.content}", style = MaterialTheme.typography.subtitle1)
                    Text("ID: ${block.id.take(8)}...", style = MaterialTheme.typography.caption)
                    Spacer(Modifier.height(4.dp))
                    Text("Position (dp): (${block.position.x.toInt()}, ${block.position.y.toInt()})")
                    Text("Size (dp): (${block.size.x.toInt()}, ${block.size.y.toInt()})")
                    Spacer(Modifier.height(4.dp))
                    Text("Input Point (dp): (${block.inputPosition.x.toInt()}, ${block.inputPosition.y.toInt()})")
                    Text("Output Point (dp): (${block.outputPosition.x.toInt()}, ${block.outputPosition.y.toInt()})")

                    Spacer(Modifier.height(4.dp))
                    Text("Position in pixels:", style = MaterialTheme.typography.subtitle2)
                    val pixelPos = block.position.toPixelOffset(density)
                    Text("(${pixelPos.x.toInt()}, ${pixelPos.y.toInt()})")
                }
            }
        }
    }
}

// New component for drawing a debugging grid
@Composable
fun DebugGrid(
    clickedPosition: Offset?,
    clickedDpPosition: Offset?,
    hoverPosition: Offset,
    hoverDpPosition: Offset,
    connectionPoints: List<Triple<String, ConnectionPointType, Offset>>,
    blocks: List<Block>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Grid spacing
        val gridSpacing = 50f

        // Draw vertical lines
        for (x in 0..(width.toInt() / gridSpacing.toInt())) {
            val xPos = x * gridSpacing
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(xPos, 0f),
                end = Offset(xPos, height),
                strokeWidth = 1f
            )

            if (x % 2 == 0) {
                drawCircle(
                    color = Color.Gray,
                    radius = 2f,
                    center = Offset(xPos, 0f)
                )
            }
        }

        // Draw horizontal lines
        for (y in 0..(height.toInt() / gridSpacing.toInt())) {
            val yPos = y * gridSpacing
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, yPos),
                end = Offset(width, yPos),
                strokeWidth = 1f
            )

            if (y % 2 == 0) {
                drawCircle(
                    color = Color.Gray,
                    radius = 2f,
                    center = Offset(0f, yPos)
                )
            }
        }

        // Draw block bounds for debugging
        for (block in blocks) {
            // Convert dp to pixels for drawing
            val pixelLeft = block.position.x * density
            val pixelTop = block.position.y * density
            val pixelWidth = block.size.x * density
            val pixelHeight = block.size.y * density

            // Draw a rectangle to show the block bounds
            drawRect(
                color = Color.Magenta.copy(alpha = 0.2f),
                topLeft = Offset(pixelLeft, pixelTop),
                size = androidx.compose.ui.geometry.Size(
                    pixelWidth,
                    pixelHeight
                )
            )

            // Print debug info
            println("Block ${block.content} - Drawing rectangle at ($pixelLeft, $pixelTop) with size ($pixelWidth, $pixelHeight)")
            println("  Original dp pos: ${block.position.x}, ${block.position.y}")
            println("  Multiplied by density: $density")
        }

        // Draw connection points for debugging
        for ((blockId, type, position) in connectionPoints) {
            val color = when (type) {
                ConnectionPointType.INPUT -> Color.Green.copy(alpha = 0.7f)
                ConnectionPointType.OUTPUT -> Color.Red.copy(alpha = 0.7f)
            }

            // Convert dp to pixels for drawing
            val pixelPos = position.toPixelOffset(density)

            // Draw a circle to indicate the connection point
            val size = 6f
            drawCircle(
                color = color,
                radius = size,
                center = pixelPos
            )

            // Draw a cross inside to make it more visible
            drawLine(
                color = Color.Black.copy(alpha = 0.5f),
                start = Offset(pixelPos.x - size, pixelPos.y),
                end = Offset(pixelPos.x + size, pixelPos.y),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.5f),
                start = Offset(pixelPos.x, pixelPos.y - size),
                end = Offset(pixelPos.x, pixelPos.y + size),
                strokeWidth = 1f
            )
        }

        // Draw a crosshair at the clicked position
        clickedPosition?.let { position ->
            drawLine(
                color = Color.Red,
                start = Offset(position.x - 10, position.y),
                end = Offset(position.x + 10, position.y),
                strokeWidth = 2f
            )
            drawLine(
                color = Color.Red,
                start = Offset(position.x, position.y - 10),
                end = Offset(position.x, position.y + 10),
                strokeWidth = 2f
            )

            // Draw a circle at the clicked position
            drawCircle(
                color = Color.Red.copy(alpha = 0.3f),
                radius = 10f,
                center = position
            )

            // Also draw a circle at the dp position converted back to pixels
            clickedDpPosition?.let { dpPos ->
                val pixelPos = dpPos.toPixelOffset(density)
                drawCircle(
                    color = Color.Yellow.copy(alpha = 0.3f),
                    radius = 10f,
                    center = pixelPos
                )
            }
        }
        
        // Draw a crosshair at the hover position
        drawLine(
            color = Color.Blue.copy(alpha = 0.5f),
            start = Offset(hoverPosition.x - 10, hoverPosition.y),
            end = Offset(hoverPosition.x + 10, hoverPosition.y),
            strokeWidth = 1f
        )
        drawLine(
            color = Color.Blue.copy(alpha = 0.5f),
            start = Offset(hoverPosition.x, hoverPosition.y - 10),
            end = Offset(hoverPosition.x, hoverPosition.y + 10),
            strokeWidth = 1f
        )
    }
    
    // Add coordinate labels outside the Canvas
    Box(modifier = modifier.fillMaxSize()) {
        // Add some coordinate labels on the top edge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (x in 0..10) {
                val xPos = x * 100
                Text(
                    text = "$xPos",
                    color = Color.Gray,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        // Add some coordinate labels on the left edge
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            for (y in 0..10) {
                val yPos = y * 50
                Text(
                    text = "$yPos",
                    color = Color.Gray,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(start = 2.dp)
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