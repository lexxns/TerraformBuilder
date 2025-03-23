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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

// Basic data structures
enum class ConnectionPointType {
    INPUT, OUTPUT
}

/**
 * Extension functions to convert between Dp and pixels when needed
 */
fun Offset.toDpOffset(density: Float): Offset {
    return Offset(x / density, y / density)
}

fun Offset.toPixelOffset(density: Float): Offset {
    return Offset(x * density, y * density)
}

/**
 * Block class that stores all positions in dp
 */
data class Block(
    val id: String,
    val type: BlockType,
    private var _position: Offset = Offset.Zero,  // Position in dp
    var content: String,
    private var _size: Offset = Offset.Zero,      // Size in dp
    var inputPosition: Offset = Offset.Zero,  // Position of the input connection point in dp
    var outputPosition: Offset = Offset.Zero  // Position of the output connection point in dp
) {
    // Property accessors with auto-update of connection points
    var position: Offset
        get() = _position
        set(value) {
            _position = value
            updateConnectionPoints()
        }
        
    var size: Offset
        get() = _size
        set(value) {
            _size = value
            updateConnectionPoints()
        }
    
    // Returns the position of the specified connection point in dp
    fun getConnectionPointPosition(type: ConnectionPointType): Offset {
        return when (type) {
            ConnectionPointType.INPUT -> inputPosition
            ConnectionPointType.OUTPUT -> outputPosition
        }
    }
    
    // Updates the connection point positions based on the block's position and size
    fun updateConnectionPoints() {
        // Calculate connection points directly in dp
        // For input: center of left edge
        inputPosition = _position + Offset(0f, _size.y / 2f)
        
        // For output: center of right edge
        outputPosition = _position + Offset(_size.x, _size.y / 2f)
        
        // Print debug info
        println("Block $content position: $_position, size: $_size")
        println("Input at: $inputPosition, Output at: $outputPosition")
        println("--------------------")
    }
}

enum class BlockType {
    COMPUTE, DATABASE, NETWORKING, SECURITY, INTEGRATION, MONITORING
}

// State for tracking active connection being drawn
class ConnectionDragState {
    var isActive by mutableStateOf(false)
    var sourceBlock by mutableStateOf<Block?>(null)
    var sourcePointType by mutableStateOf<ConnectionPointType?>(null)
    var currentPosition by mutableStateOf(Offset.Zero)  // in dp
}

// Connection class with direct references to blocks
data class Connection(
    val id: String = UUID.randomUUID().toString(),
    val sourceBlock: Block,
    val targetBlock: Block
) {
    val sourcePointType = ConnectionPointType.OUTPUT
    val targetPointType = ConnectionPointType.INPUT
    
    // Get start point position (source block's output) in dp
    fun getStartPosition(): Offset {
        return sourceBlock.getConnectionPointPosition(sourcePointType)
    }
    
    // Get end point position (target block's input) in dp
    fun getEndPosition(): Offset {
        return targetBlock.getConnectionPointPosition(targetPointType)
    }
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
        _connections.removeAll { it.sourceBlock.id == blockId || it.targetBlock.id == blockId }
    }

    fun updateBlockPosition(blockId: String, newPosition: Offset) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index].position = newPosition
            // Connection points are updated automatically by the Block class
        }
    }

    fun updateBlockSize(blockId: String, size: Offset) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index].size = size
            // Connection points are updated automatically by the Block class
        }
    }

    fun updateBlockContent(blockId: String, newContent: String) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index].content = newContent
        }
    }

    fun addConnection(sourceBlock: Block, targetBlock: Block) {
        // Check if connection already exists
        val exists = _connections.any {
            it.sourceBlock.id == sourceBlock.id && it.targetBlock.id == targetBlock.id
        }

        if (!exists) {
            _connections.add(Connection(sourceBlock = sourceBlock, targetBlock = targetBlock))
        }
    }

    fun removeConnection(connectionId: String) {
        _connections.removeAll { it.id == connectionId }
    }

    fun startConnectionDrag(block: Block, pointType: ConnectionPointType) {
        dragState.isActive = true
        dragState.sourceBlock = block
        dragState.sourcePointType = pointType
        dragState.currentPosition = block.getConnectionPointPosition(pointType)
    }

    fun updateDragPosition(position: Offset) {
        // Position is already in dp
        dragState.currentPosition = position
    }

    fun endConnectionDrag(position: Offset) {
        if (!dragState.isActive || dragState.sourceBlock == null || dragState.sourcePointType == null) return

        // Position is already in dp
        val endPosition = position
        
        // Find if there's a connection point near the end position
        val nearbyBlocks = _blocks.map { block ->
            Pair(block, findNearestConnectionPoint(block, endPosition))
        }.filter { (block, result) ->
            // Only consider valid results, not from the same block, and with compatible connection types
            result.first && block.id != dragState.sourceBlock!!.id && 
                    result.second != dragState.sourcePointType
        }

        if (nearbyBlocks.isNotEmpty()) {
            val (targetBlock, _) = nearbyBlocks.minByOrNull { 
                val (_, result) = it
                result.third // distance
            } ?: return

            // Determine source and target based on connection point types
            if (dragState.sourcePointType == ConnectionPointType.OUTPUT) {
                // Source block's output to target block's input
                addConnection(dragState.sourceBlock!!, targetBlock)
            } else {
                // Target block's output to source block's input
                addConnection(targetBlock, dragState.sourceBlock!!)
            }
        }

        // Reset drag state
        dragState.isActive = false
        dragState.sourceBlock = null
        dragState.sourcePointType = null
    }
    
    // Helper function to find the nearest connection point on a block
    // Returns Triple(isNearby, connectionPointType, distance)
    private fun findNearestConnectionPoint(block: Block, position: Offset): Triple<Boolean, ConnectionPointType, Float> {
        val inputDistance = (block.inputPosition - position).getDistance()
        val outputDistance = (block.outputPosition - position).getDistance()
        
        val minDistance = minOf(inputDistance, outputDistance)
        val nearestType = if (inputDistance < outputDistance) 
            ConnectionPointType.INPUT else ConnectionPointType.OUTPUT
            
        // Distance threshold in dp
        return Triple(minDistance < 20f, nearestType, minDistance)
    }
}

// Block creation helper
fun createBlock(
    id: String,
    type: BlockType,
    content: String,
): Block {
    return Block(
        id = id,
        type = type,
        content = content
    )
}

// UI Components

// Block View Component
@Composable
fun BlockView(
    block: Block,
    onDragEnd: (Offset) -> Unit,
    onRename: (String) -> Unit,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit,
    onUpdateBlockSize: (String, Offset) -> Unit
) {
    var position by remember { mutableStateOf(block.position) }
    var isEditing by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(block.content)) }
    val density = LocalDensity.current.density
    
    // Update our local position when the block's position changes externally
    LaunchedEffect(block.position) {
        position = block.position
    }

    Box(
        modifier = Modifier
            .offset(position.x.dp, position.y.dp)
            .onGloballyPositioned { coordinates ->
                // Update the block size in dp units
                val size = Offset(
                    coordinates.size.width / density,
                    coordinates.size.height / density
                )
                
                // Only update size if it has changed to avoid unnecessary recompositions
                if (block.size != size) {
                    onUpdateBlockSize(block.id, size)
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
                type = ConnectionPointType.INPUT,
                onConnectionStart = {
                    onConnectionDragStart(block, ConnectionPointType.INPUT)
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
                            onDragEnd = { 
                                onDragEnd(position) 
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Update position in dp coordinates
                                val newPosition = position + Offset(dragAmount.x / density, dragAmount.y / density)
                                position = newPosition
                                
                                // Update the block position in the BlockState during drag as well
                                // This ensures connection points are updated in real-time
                                onDragEnd(newPosition)
                                
                                // For debugging only
                                println("Dragging block ${block.content} to dp: $newPosition")
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
                }            
            }

            // Output connection point
            ConnectionPointView(
                type = ConnectionPointType.OUTPUT,
                onConnectionStart = {
                    onConnectionDragStart(block, ConnectionPointType.OUTPUT)
                }
            )
        }
    }
}

// Connection Point View
@Composable
fun ConnectionPointView(
    type: ConnectionPointType,
    onConnectionStart: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = when (type) {
                    ConnectionPointType.INPUT -> Color.Green
                    ConnectionPointType.OUTPUT -> Color.Red
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
    dragState: ConnectionDragState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // Convert dp to pixels at the time of drawing
        val dpToPx: (Offset) -> Offset = { dp -> 
            Offset(dp.x * density, dp.y * density)
        }
        
        // Draw permanent connections
        connections.forEach { connection ->
            val startPos = dpToPx(connection.getStartPosition())
            val endPos = dpToPx(connection.getEndPosition())
            drawConnection(startPos, endPos)
        }

        // Draw connection being dragged
        if (dragState.isActive && dragState.sourceBlock != null && dragState.sourcePointType != null) {
            val sourcePoint = dpToPx(
                dragState.sourceBlock!!.getConnectionPointPosition(dragState.sourcePointType!!)
            )
            val targetPoint = dpToPx(dragState.currentPosition)
            
            // Draw from source to current position
            if (dragState.sourcePointType == ConnectionPointType.OUTPUT) {
                drawConnection(sourcePoint, targetPoint)
            } else {
                drawConnection(targetPoint, sourcePoint)
            }
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

// Helper extension to calculate distance between points
fun Offset.getDistance(): Float {
    return sqrt(x * x + y * y)
}

// Add extension function to add two offsets
operator fun Offset.plus(other: Offset): Offset {
    return Offset(this.x + other.x, this.y + other.y)
}

// Add extension function to subtract offsets
operator fun Offset.minus(other: Offset): Offset {
    return Offset(this.x - other.x, this.y - other.y)
}