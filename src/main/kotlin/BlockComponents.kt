import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas


data class ConnectionPoint(
    val id: String,
    val type: ConnectionType,
    val position: Offset
)

enum class ConnectionType {
    INPUT,
    OUTPUT
}

data class Connection(
    val id: String,
    val fromBlockId: String,
    val toBlockId: String,
    val fromPointId: String,
    val toPointId: String
)

data class Block(
    val id: String,
    val type: BlockType,
    val position: Offset = Offset.Zero,
    var content: String,
    val connectionPoints: List<ConnectionPoint> = emptyList()
)

enum class BlockType {
    COMPUTE,      // Lambda, EC2, ECS
    DATABASE,     // DynamoDB, RDS, S3
    NETWORKING,   // VPC, Subnet, Security Group
    SECURITY,     // IAM, KMS, Secrets Manager
    INTEGRATION,  // API Gateway, SQS, SNS
    MONITORING    // CloudWatch, X-Ray
}

@Composable
fun blockItem(
    block: Block,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick)
    ) {
        blockContent(
            block = block,
            isEditing = false,
            textFieldValue = TextFieldValue(block.content),
            onTextChange = {},
            onRename = {},
            onEditingChange = {}
        )
    }
}

class BlockState {
    private val _blocks = mutableStateListOf<Block>()
    val blocks: List<Block> = _blocks
    
    private val _connections = mutableStateListOf<Connection>()
    val connections: List<Connection> = _connections

    fun addBlock(block: Block) {
        _blocks.add(block)
    }

    fun removeBlock(block: Block) {
        _blocks.remove(block)
        // Remove all connections involving this block
        _connections.removeAll { it.fromBlockId == block.id || it.toBlockId == block.id }
    }

    fun updateBlockPosition(blockId: String, newPosition: Offset) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index] = _blocks[index].copy(position = newPosition)
        }
    }

    fun updateBlockContent(blockId: String, newContent: String) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index] = _blocks[index].copy(content = newContent)
        }
    }

    fun addConnection(connection: Connection) {
        _connections.add(connection)
    }

    fun removeConnection(connectionId: String) {
        _connections.removeAll { it.id == connectionId }
    }
}

@Composable
fun draggableBlock(
    block: Block,
    onDragEnd: (Offset) -> Unit,
    onRename: (String) -> Unit,
    onConnectionPointSelected: (ConnectionPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(block.position) }
    var isEditing by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(block.content)) }
    
    Box(
        modifier = modifier
            .offset(position.x.dp, position.y.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd(position) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        position += dragAmount
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Input connection point
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.Green, androidx.compose.foundation.shape.CircleShape)
                    .clickable { 
                        block.connectionPoints.find { it.type == ConnectionType.INPUT }?.let { 
                            onConnectionPointSelected(it) 
                        }
                    }
            )
            
            // Block content
            blockContent(
                block = block,
                isEditing = isEditing,
                textFieldValue = textFieldValue,
                onTextChange = { textFieldValue = it },
                onRename = { 
                    onRename(it)
                    isEditing = false
                },
                onEditingChange = { isEditing = it }
            )
            
            // Output connection point
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.Red, androidx.compose.foundation.shape.CircleShape)
                    .clickable { 
                        block.connectionPoints.find { it.type == ConnectionType.OUTPUT }?.let { 
                            onConnectionPointSelected(it) 
                        }
                    }
            )
        }
    }
}

@Composable
fun blockContent(
    block: Block,
    isEditing: Boolean,
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onRename: (String) -> Unit,
    onEditingChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = when (block.type) {
                    BlockType.COMPUTE -> Color(0xFF4C97FF)
                    BlockType.DATABASE -> Color(0xFFFFAB19)
                    BlockType.NETWORKING -> Color(0xFFFF8C1A)
                    BlockType.SECURITY -> Color(0xFF40BF4A)
                    BlockType.INTEGRATION -> Color(0xFF4C97FF)
                    BlockType.MONITORING -> Color(0xFFFFAB19)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onEditingChange(true) }
                )
            }
    ) {
        if (isEditing) {
            androidx.compose.material.TextField(
                value = textFieldValue,
                onValueChange = onTextChange,
                modifier = Modifier.widthIn(min = 100.dp),
                textStyle = TextStyle(color = Color.White),
                colors = androidx.compose.material.TextFieldDefaults.textFieldColors(
                    textColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        } else {
            Text(
                text = block.content,
                color = Color.White,
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
fun connectionLine(
    connection: Connection,
    fromBlock: Block,
    toBlock: Block,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val fromPoint = fromBlock.connectionPoints.find { it.id == connection.fromPointId }
        val toPoint = toBlock.connectionPoints.find { it.id == connection.toPointId }
        
        if (fromPoint != null && toPoint != null) {
            drawLine(
                color = Color.Black,
                start = fromPoint.position + fromBlock.position,
                end = toPoint.position + toBlock.position,
                strokeWidth = 2f
            )
        }
    }
}

// Helper function to create blocks with connection points
fun createBlockWithConnections(
    id: String,
    type: BlockType,
    content: String,
    position: Offset = Offset.Zero
): Block {
    return Block(
        id = id,
        type = type,
        position = position,
        content = content,
        connectionPoints = listOf(
            ConnectionPoint(
                id = "${id}_input",
                type = ConnectionType.INPUT,
                position = Offset(0f, 0.5f) // Left side
            ),
            ConnectionPoint(
                id = "${id}_output",
                type = ConnectionType.OUTPUT,
                position = Offset(1f, 0.5f) // Right side
            )
        )
    )
} 