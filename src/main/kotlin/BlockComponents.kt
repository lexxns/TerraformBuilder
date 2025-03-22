// This is a complete, simplified approach for your connection system
// Replace your entire BlockComponents.kt file with this:

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

// Basic data structures
data class ConnectionPoint(
    val id: String,
    val type: ConnectionType,
    val relativePosition: Offset = Offset.Zero,
    var absolutePosition: Offset = Offset.Zero
)

enum class ConnectionType {
    INPUT, OUTPUT
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
    val connectionPoints: List<ConnectionPoint> = emptyList(),
    var size: Offset = Offset.Zero
)

enum class BlockType {
    COMPUTE, DATABASE, NETWORKING, SECURITY, INTEGRATION, MONITORING
}

// State for tracking active connection being drawn
class ConnectionDragState {
    var isActive by mutableStateOf(false)
    var startPoint by mutableStateOf<ConnectionPoint?>(null)
    var currentPosition by mutableStateOf(Offset.Zero)
    var sourceBlockId by mutableStateOf<String?>(null)
}

// Block state management
class BlockState {
    private val _blocks = mutableStateListOf<Block>()
    val blocks: List<Block> = _blocks

    private val _connections = mutableStateListOf<Connection>()
    val connections: List<Connection> = _connections

    val dragState = ConnectionDragState()

    fun addBlock(block: Block) {
        _blocks.add(block)
    }

    fun removeBlock(blockId: String) {
        val block = _blocks.find { it.id == blockId } ?: return
        _blocks.remove(block)
        _connections.removeAll { it.fromBlockId == blockId || it.toBlockId == blockId }
    }

    fun updateBlockPosition(blockId: String, newPosition: Offset) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index] = _blocks[index].copy(position = newPosition)
        }
    }

    fun updateBlockSize(blockId: String, size: Offset) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index].size = size
        }
    }

    fun updateConnectionPointPosition(blockId: String, pointId: String, position: Offset) {
        val blockIndex = _blocks.indexOfFirst { it.id == blockId }
        if (blockIndex != -1) {
            val pointIndex = _blocks[blockIndex].connectionPoints.indexOfFirst { it.id == pointId }
            if (pointIndex != -1) {
                _blocks[blockIndex].connectionPoints[pointIndex].absolutePosition = position
            }
        }
    }

    fun updateBlockContent(blockId: String, newContent: String) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index].content = newContent
        }
    }

    fun addConnection(connection: Connection) {
        // Check if connection already exists
        val exists = _connections.any {
            it.fromBlockId == connection.fromBlockId &&
                    it.toBlockId == connection.toBlockId &&
                    it.fromPointId == connection.fromPointId &&
                    it.toPointId == connection.toPointId
        }

        if (!exists) {
            _connections.add(connection)
        }
    }

    fun removeConnection(connectionId: String) {
        _connections.removeAll { it.id == connectionId }
    }

    fun startConnectionDrag(point: ConnectionPoint, blockId: String) {
        dragState.isActive = true
        dragState.startPoint = point
        dragState.currentPosition = point.absolutePosition
        dragState.sourceBlockId = blockId
    }

    fun updateDragPosition(position: Offset) {
        dragState.currentPosition = position
    }

    fun endConnectionDrag(position: Offset) {
        if (!dragState.isActive) return

        // Find if there's a connection point near the end position
        val nearbyPoints = _blocks.flatMap { block ->
            block.connectionPoints.map { point ->
                Triple(block.id, point, (point.absolutePosition - position).getDistance())
            }
        }.filter { (blockId, point, distance) ->
            // Only consider points within 30 pixels and not from the same block
            distance < 30f && blockId != dragState.sourceBlockId &&
                    // Ensure we're connecting input to output (not input to input or output to output)
                    ((dragState.startPoint?.type == ConnectionType.OUTPUT && point.type == ConnectionType.INPUT) ||
                            (dragState.startPoint?.type == ConnectionType.INPUT && point.type == ConnectionType.OUTPUT))
        }.minByOrNull { it.third }

        if (nearbyPoints != null && dragState.startPoint != null && dragState.sourceBlockId != null) {
            val (targetBlockId, targetPoint, _) = nearbyPoints

            // Determine which is source and which is target based on connection type
            val (fromBlockId, fromPointId, toBlockId, toPointId) = if (dragState.startPoint!!.type == ConnectionType.OUTPUT) {
                Quad(dragState.sourceBlockId!!, dragState.startPoint!!.id, targetBlockId, targetPoint.id)
            } else {
                Quad(targetBlockId, targetPoint.id, dragState.sourceBlockId!!, dragState.startPoint!!.id)
            }

            // Add the new connection
            addConnection(
                Connection(
                    id = UUID.randomUUID().toString(),
                    fromBlockId = fromBlockId,
                    toBlockId = toBlockId,
                    fromPointId = fromPointId,
                    toPointId = toPointId
                )
            )
        }

        // Reset drag state
        dragState.isActive = false
        dragState.startPoint = null
        dragState.sourceBlockId = null
    }
}

// Helper data class
data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// Block creation helper
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
                type = ConnectionType.INPUT
            ),
            ConnectionPoint(
                id = "${id}_output",
                type = ConnectionType.OUTPUT
            )
        )
    )
}

// UI Components

// Block View Component
@Composable
fun BlockView(
    block: Block,
    onDragEnd: (Offset) -> Unit,
    onRename: (String) -> Unit,
    onConnectionDragStart: (ConnectionPoint, String) -> Unit,
    onUpdateBlockSize: (String, Offset) -> Unit,
    onUpdateConnectionPointPosition: (String, String, Offset) -> Unit
) {
    var position by remember { mutableStateOf(block.position) }
    var isEditing by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(block.content)) }

    Box(
        modifier = Modifier
            .offset(position.x.dp, position.y.dp)
            .onGloballyPositioned { coordinates ->
                // Update the block size when it's positioned
                val size = Offset(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
                onUpdateBlockSize(block.id, size)

                // Update the absolute positions of connection points
                block.connectionPoints.forEach { point ->
                    val pointPosition = when (point.type) {
                        ConnectionType.INPUT -> Offset(0f, coordinates.size.height / 2f)
                        ConnectionType.OUTPUT -> Offset(coordinates.size.width.toFloat(), coordinates.size.height / 2f)
                    }
                    val absolutePos = coordinates.positionInWindow() + pointPosition
                    onUpdateConnectionPointPosition(block.id, point.id, absolutePos)
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Input connection point
            ConnectionPointView(
                type = ConnectionType.INPUT,
                onConnectionStart = {
                    val inputPoint = block.connectionPoints.find { it.type == ConnectionType.INPUT }
                    inputPoint?.let { onConnectionDragStart(it, block.id) }
                }
            )

            // Block content - this is the part that can be dragged
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
                    .border(
                        width = 2.dp,
                        color = Color.Black,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { isEditing = true }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onDragEnd(position) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                position += dragAmount

                                // When dragging the block, update connection point positions
                                block.connectionPoints.forEach { point ->
                                    val pointPosition = when (point.type) {
                                        ConnectionType.INPUT -> Offset(0f, block.size.y / 2f)
                                        ConnectionType.OUTPUT -> Offset(block.size.x, block.size.y / 2f)
                                    }
                                    val absolutePos = Offset(
                                        position.x + pointPosition.x,
                                        position.y + pointPosition.y
                                    )
                                    onUpdateConnectionPointPosition(block.id, point.id, absolutePos)
                                }
                            }
                        )
                    }
            ) {
                if (isEditing) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            onRename(it.text)
                        },
                        modifier = Modifier.widthIn(min = 100.dp),
                        textStyle = TextStyle(color = Color.White),
                        colors = TextFieldDefaults.textFieldColors(
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
                }            }

            // Output connection point
            ConnectionPointView(
                type = ConnectionType.OUTPUT,
                onConnectionStart = {
                    val outputPoint = block.connectionPoints.find { it.type == ConnectionType.OUTPUT }
                    outputPoint?.let { onConnectionDragStart(it, block.id) }
                }
            )
        }
    }
}

// Connection Point View
@Composable
fun ConnectionPointView(
    type: ConnectionType,
    onConnectionStart: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = when (type) {
                    ConnectionType.INPUT -> Color.Green
                    ConnectionType.OUTPUT -> Color.Red
                },
                shape = CircleShape
            )
            .clickable { onConnectionStart() }
    )
}

// Connections Canvas
@Composable
fun ConnectionsCanvas(
    connections: List<Connection>,
    blocks: List<Block>,
    dragState: ConnectionDragState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw permanent connections
        connections.forEach { connection ->
            val fromBlock = blocks.find { it.id == connection.fromBlockId }
            val toBlock = blocks.find { it.id == connection.toBlockId }

            val fromPoint = fromBlock?.connectionPoints?.find { it.id == connection.fromPointId }
            val toPoint = toBlock?.connectionPoints?.find { it.id == connection.toPointId }

            if (fromPoint != null && toPoint != null) {
                drawConnection(fromPoint.absolutePosition, toPoint.absolutePosition)
            }
        }

        // Draw connection being dragged
        if (dragState.isActive && dragState.startPoint != null) {
            drawConnection(dragState.startPoint!!.absolutePosition, dragState.currentPosition)
        }
    }
}

// Helper function to draw a connection with an arrow
fun DrawScope.drawConnection(start: Offset, end: Offset) {
    // Draw the line
    drawLine(
        color = Color.Black,
        start = start,
        end = end,
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )

    // Draw arrow at the end
    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowLength = 10f
    val arrowAngle = 30f * (PI / 180f)

    val arrowPoint1 = Offset(
        (end.x - arrowLength * cos(angle - arrowAngle)).toFloat(),
        (end.y - arrowLength * sin(angle - arrowAngle)).toFloat()
    )
    val arrowPoint2 = Offset(
        (end.x - arrowLength * cos(angle + arrowAngle)).toFloat(),
        (end.y - arrowLength * sin(angle + arrowAngle)).toFloat()
    )

    drawLine(
        color = Color.Black,
        start = end,
        end = arrowPoint1,
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.Black,
        start = end,
        end = arrowPoint2,
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )
}

// Block Item Composable for the library panel
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
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Block content with color coding
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = when (block.type) {
                            BlockType.COMPUTE -> Color(0xFF4C97FF)
                            BlockType.DATABASE -> Color(0xFFFFAB19)
                            BlockType.NETWORKING -> Color(0xFFFF8C1A)
                            BlockType.SECURITY -> Color(0xFF40BF4A)
                            BlockType.INTEGRATION -> Color(0xFF4C97FF)
                            BlockType.MONITORING -> Color(0xFFFFAB19)
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
                    .border(1.dp, Color.Black, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = block.content,
                style = MaterialTheme.typography.body2
            )
        }
    }
}