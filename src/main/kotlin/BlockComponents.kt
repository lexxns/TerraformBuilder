package terraformbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.unit.dp
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

// Add new imports
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

enum class ConnectionPointType {
    INPUT, OUTPUT
}

/**
 * Extension functions to convert between Dp and pixels when needed
 */
fun Offset.toDpOffset(density: Float): Offset {
    return Offset(x / density, y / density)
}

data class Block(
    val id: String,
    val type: BlockType,
    private var _position: Offset = Offset.Zero,  // Position in dp
    var content: String,
    var resourceType: ResourceType, // Type of resource (Lambda Function, S3 Bucket, etc.)
    private var _size: Offset = Offset.Zero,      // Size in dp
    var inputPosition: Offset = Offset.Zero,  // Position of the input connection point in dp
    var outputPosition: Offset = Offset.Zero,  // Position of the output connection point in dp
    val properties: MutableMap<String, String> = mutableMapOf()  // Map of property name to value
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
    private fun updateConnectionPoints() {
        // For input: left edge, vertically centered
        // Position it slightly to the left to appear as part of the block
        inputPosition = _position + Offset(-6f, _size.y / 2f)
        
        // For output: right edge, vertically centered
        // Position it slightly to the right to appear as part of the block
        outputPosition = _position + Offset(_size.x + 6f, _size.y / 2f)
        
        // Print debug info
        println("Block $content position: $_position, size: $_size")
        println("Input at: $inputPosition, Output at: $outputPosition")
        println("--------------------")
    }
    
    // Initialize default property values from TerraformProperties
    fun initializeDefaultProperties() {
        val terraformProps = TerraformProperties.getPropertiesForBlock(this)
        
        terraformProps.forEach { prop ->
            // Only set if not already set and has a default value
            if (!properties.containsKey(prop.name) && prop.default != null) {
                properties[prop.name] = prop.default
            }
        }
    }
    
    // Set a property value
    fun setProperty(name: String, value: String) {
        properties[name] = value
    }
    
    // Get a property value or default
    fun getProperty(name: String): String? {
        return properties[name]
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
    private val sourcePointType = ConnectionPointType.OUTPUT
    private val targetPointType = ConnectionPointType.INPUT
    
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
        // Initialize default properties when adding a block
        block.initializeDefaultProperties()
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
    
    // Update a specific property for a block
    fun updateBlockProperty(blockId: String, propertyName: String, propertyValue: String) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index != -1) {
            _blocks[index].setProperty(propertyName, propertyValue)
        }
    }
    
    // Get a property value for a block
    fun getBlockProperty(blockId: String, propertyName: String): String? {
        val block = _blocks.find { it.id == blockId } ?: return null
        return block.getProperty(propertyName)
    }
    
    // Get all properties for a block
    fun getBlockProperties(blockId: String): Map<String, String> {
        val block = _blocks.find { it.id == blockId } ?: return emptyMap()
        return block.properties.toMap()
    }

    private fun addConnection(sourceBlock: Block, targetBlock: Block) {
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
        println("BLOCKSTATE: Starting connection drag from ${block.content} with point type $pointType")
        
        // Initialize a new drag state
        dragState.isActive = true
        dragState.sourceBlock = block
        dragState.sourcePointType = pointType
        
        // Set the initial position to the connection point's position
        val connectionPointPosition = block.getConnectionPointPosition(pointType)
        dragState.currentPosition = connectionPointPosition
        
        println("BLOCKSTATE: Connection drag initialized at $connectionPointPosition")
    }

    fun updateDragPosition(position: Offset) {
        // Position is already in dp
        dragState.currentPosition = position
    }

    fun endConnectionDrag(position: Offset) {
        if (!dragState.isActive || dragState.sourceBlock == null || dragState.sourcePointType == null) return

        // Position is already in dp
        val endPosition = position
        
        // Debug the connection attempt
        println("Ending connection drag at position: $endPosition")
        println("Source block: ${dragState.sourceBlock!!.content}, point type: ${dragState.sourcePointType}")
        
        // Find if there's a connection point near the end position
        val nearbyBlocks = _blocks.map { block ->
            val result = findNearestConnectionPoint(block, endPosition)
            println("Checking block ${block.content}: ${result.first}, type: ${result.second}, distance: ${result.third}")
            Pair(block, result)
        }.filter { (block, result) ->
            // Only consider valid results, not from the same block, and with compatible connection types
            val validBlock = block.id != dragState.sourceBlock!!.id
            val validDistance = result.first // isNearby
            val compatibleTypes = result.second != dragState.sourcePointType
            
            println("Block ${block.content} valid? " +
                "different block: $validBlock, " +
                "nearby: $validDistance, " +
                "compatible types: $compatibleTypes")
            
            validBlock && validDistance && compatibleTypes
        }

        println("Found ${nearbyBlocks.size} nearby compatible blocks")
        
        if (nearbyBlocks.isNotEmpty()) {
            val (targetBlock, targetResult) = nearbyBlocks.minByOrNull { 
                val (_, result) = it
                result.third // distance
            } ?: return

            println("Creating connection to ${targetBlock.content} with point type ${targetResult.second}")
            
            // Determine source and target based on connection point types
            if (dragState.sourcePointType == ConnectionPointType.OUTPUT) {
                // Source block's output to target block's input
                addConnection(dragState.sourceBlock!!, targetBlock)
                println("Connected ${dragState.sourceBlock!!.content} OUTPUT -> ${targetBlock.content} INPUT")
            } else {
                // Target block's output to source block's input
                addConnection(targetBlock, dragState.sourceBlock!!)
                println("Connected ${targetBlock.content} OUTPUT -> ${dragState.sourceBlock!!.content} INPUT")
            }
        } else {
            println("No valid connection points found")
        }

        // Reset drag state
        dragState.isActive = false
        dragState.sourceBlock = null
        dragState.sourcePointType = null
    }
    
    // Helper function to find the nearest connection point on a block
    // Returns Triple(isNearby, connectionPointType, distance)
    private fun findNearestConnectionPoint(block: Block, position: Offset): Triple<Boolean, ConnectionPointType, Float> {
        val inputPosition = block.getConnectionPointPosition(ConnectionPointType.INPUT)
        val outputPosition = block.getConnectionPointPosition(ConnectionPointType.OUTPUT)
        
        val inputDistance = (inputPosition - position).getDistance()
        val outputDistance = (outputPosition - position).getDistance()
        
        val minDistance = minOf(inputDistance, outputDistance)
        val nearestType = if (inputDistance < outputDistance) 
            ConnectionPointType.INPUT else ConnectionPointType.OUTPUT
            
        // Distance threshold in dp - increased to make connections easier
        val isNearby = minDistance < 30f
        
        return Triple(isNearby, nearestType, minDistance)
    }

    fun clearAll() {
        _blocks.clear()
        _connections.clear()
        dragState.isActive = false
        dragState.sourceBlock = null
        dragState.sourcePointType = null
        dragState.currentPosition = Offset.Zero
    }
}

// Block creation helper
fun createBlock(
    id: String,
    type: BlockType,
    content: String,
    resourceType: ResourceType
): Block {
    val block = Block(
        id = id,
        type = type,
        content = content,
        resourceType = resourceType
    )
    
    // Initialize default properties
    block.initializeDefaultProperties()
    
    return block
}

// UI Components

@Composable
fun blockView(
    block: Block,
    onDragEnd: (Offset) -> Unit,
    onRename: (String) -> Unit,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit,
    onUpdateBlockSize: (String, Offset) -> Unit,
    onBlockSelected: (String) -> Unit = {},
    isHovered: Boolean = false, 
    activeDragState: ConnectionDragState? = null
) {
    var position by remember { mutableStateOf(block.position) }
    var isEditing by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(block.content)) }
    val density = LocalDensity.current.density
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Track if we've just initiated a double-click
    var isDoubleClickJustTriggered by remember { mutableStateOf(false) }
    // Track if we should ignore the next focus loss
    var ignoreFocusLoss by remember { mutableStateOf(false) }
    
    // Update our local position when the block's position changes externally
    LaunchedEffect(block.position) {
        position = block.position
    }
    
    // Request focus when editing starts
    LaunchedEffect(isEditing) {
        if (isEditing) {
            println("BLOCK-FOCUS: Requesting focus for TextField in ${block.id}")
            
            // Request focus after a small delay to let composition settle
            delay(100)
            focusRequester.requestFocus()
            
            // Try again with a longer delay
            delay(200)
            if (isEditing) {
                println("BLOCK-FOCUS: Re-requesting focus after delay")
                focusRequester.requestFocus()
            }
        }
    }

    // Reset the double-click flag after a short time
    LaunchedEffect(isDoubleClickJustTriggered) {
        if (isDoubleClickJustTriggered) {
            delay(300)
            isDoubleClickJustTriggered = false
        }
    }

    // Determine if connection points should be shown
    val showConnectionPoints = isHovered || 
        (activeDragState != null && activeDragState.isActive && activeDragState.sourceBlock?.id != block.id)
        
    // If there's an active drag, determine which points to show
    val showInputPoint = showConnectionPoints && 
        (activeDragState == null || !activeDragState.isActive || 
         activeDragState.sourcePointType == ConnectionPointType.OUTPUT)
         
    val showOutputPoint = showConnectionPoints && 
        (activeDragState == null || !activeDragState.isActive || 
         activeDragState.sourcePointType == ConnectionPointType.INPUT)

    // The outermost Box that positions the block and handles size updates
    Box(
        modifier = Modifier
            .offset(position.x.dp, position.y.dp)
            .onGloballyPositioned { coordinates ->
                // Update the block size in dp units
                val size = Offset(
                    coordinates.size.width / density,
                    coordinates.size.height / density
                )
                
                if (block.size != size) {
                    onUpdateBlockSize(block.id, size)
                }
            }
    ) {
        // Use a fixed stack order with zIndex to ensure proper layering
        
        // First, draw both connection points if needed (they should appear behind the block)
        if (showInputPoint) {
            Box(
                modifier = Modifier
                    .offset((-10).dp, 0.dp)
                    .align(Alignment.CenterStart)
                    .zIndex(0f)
            ) {
                connectionPointView(
                    type = ConnectionPointType.INPUT,
                    onConnectionStart = {
                        println("BLOCK: Starting INPUT connection for block ${block.content}")
                        onConnectionDragStart(block, ConnectionPointType.INPUT)
                    }
                )
            }
        }
        
        if (showOutputPoint) {
            Box(
                modifier = Modifier
                    .offset(10.dp, 0.dp)
                    .align(Alignment.CenterEnd)
                    .zIndex(0f)
            ) {
                connectionPointView(
                    type = ConnectionPointType.OUTPUT,
                    onConnectionStart = {
                        println("BLOCK: Starting OUTPUT connection for block ${block.content}")
                        onConnectionDragStart(block, ConnectionPointType.OUTPUT)
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .zIndex(1f) // Higher z-index ensures block appears above connection points
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
                // Handle clicks directly at the block level
                .pointerInput("tap-detection") {
                    detectTapGestures(
                        onTap = { 
                            // Only process single-click if not editing and not right after double-click
                            if (!isEditing && !isDoubleClickJustTriggered) {
                                println("BLOCK-CLICK: Selected block ${block.id}")
                                onBlockSelected(block.id)
                            }
                        },
                        onDoubleTap = {
                            println("BLOCK-DOUBLE-CLICK: Setting editing mode for ${block.id}")
                            // Mark as double-click triggered to prevent single-click firing
                            isDoubleClickJustTriggered = true
                            // Set flag to ignore the initial focus loss that might happen
                            ignoreFocusLoss = true
                            // Enter edit mode
                            isEditing = true
                            textFieldValue = TextFieldValue(block.content)
                        }
                    )
                }
                // Separate pointer input for drag
                .pointerInput("drag") {
                    detectDragGestures(
                        onDragStart = {
                            println("BLOCK-DRAG-START: Started dragging block ${block.id}")
                            // Cancel editing if dragging starts
                            if (isEditing) {
                                isEditing = false
                                onRename(textFieldValue.text)
                            }
                        },
                        onDragEnd = { 
                            println("BLOCK-DRAG-END: Ended dragging block ${block.id}")
                            onDragEnd(position) 
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Update position in dp coordinates
                            val newPosition = position + Offset(dragAmount.x / density, dragAmount.y / density)
                            position = newPosition
                            onDragEnd(newPosition)
                        }
                    )
                }
        ) {
            // Block content
            if (isEditing) {
                // Simple BasicTextField with simpler focus handling
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        println("BLOCK-EDIT: Text changed to: ${it.text}")
                        textFieldValue = it
                        onRename(it.text)
                    },
                    modifier = Modifier
                        .wrapContentWidth()
                        .focusRequester(focusRequester)
                        .zIndex(100f)
                        .onFocusChanged { focusState ->
                            println("BLOCK-FOCUS: Focus state changed to: ${focusState.isFocused}, ignoreFocusLoss=$ignoreFocusLoss")
                            
                            if (!focusState.isFocused && isEditing) {
                                if (ignoreFocusLoss) {
                                    // First focus loss after starting edit - ignore it
                                    println("BLOCK-FOCUS: Ignoring initial focus loss")
                                    ignoreFocusLoss = false
                                    // Request focus again
                                    focusRequester.requestFocus()
                                } else {
                                    // Real focus loss - exit edit mode
                                    println("BLOCK-FOCUS: Real focus loss, exiting edit mode")
                                    isEditing = false
                                    // No need to call onRename here since we already update in real-time
                                }
                            }
                        },
                    textStyle = TextStyle(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            println("BLOCK-EDIT: Done pressed, exiting edit mode")
                            isEditing = false
                            // No need to call onRename again
                            focusManager.clearFocus()
                        }
                    )
                )
            } else {
                // Regular text display - no need for click handlers here
                Text(
                    text = block.content,
                    color = Color.White,
                    style = MaterialTheme.typography.body1
                )
            }
        }
    }
}

@Composable
fun connectionPointView(
    type: ConnectionPointType,
    onConnectionStart: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) } 
    
    // Use a half-circle design attached to the node
    Box(
        modifier = Modifier
            .size(12.dp, 20.dp) // Slightly smaller width for a cleaner look
            .background(
                color = when (type) {
                    ConnectionPointType.INPUT -> if (isPressed) Color.Green.copy(alpha = 1f) 
                                              else if (isHovered) Color.Green.copy(alpha = 0.9f) 
                                              else Color.Green.copy(alpha = 0.8f)
                    ConnectionPointType.OUTPUT -> if (isPressed) Color.Red.copy(alpha = 1f) 
                                               else if (isHovered) Color.Red.copy(alpha = 0.9f) 
                                               else Color.Red.copy(alpha = 0.8f)
                },
                shape = when (type) {
                    // Half circle for input (flat side on right)
                    ConnectionPointType.INPUT -> RoundedCornerShape(topStart = 50f, bottomStart = 50f, topEnd = 0f, bottomEnd = 0f)
                    // Half circle for output (flat side on left)
                    ConnectionPointType.OUTPUT -> RoundedCornerShape(topStart = 0f, bottomStart = 0f, topEnd = 50f, bottomEnd = 50f)
                }
            )
            .border(
                width = if (isPressed || isHovered) 2.dp else 1.dp,
                color = Color.Black,
                shape = when (type) {
                    // Half circle for input (flat side on right)
                    ConnectionPointType.INPUT -> RoundedCornerShape(topStart = 50f, bottomStart = 50f, topEnd = 0f, bottomEnd = 0f)
                    // Half circle for output (flat side on left)
                    ConnectionPointType.OUTPUT -> RoundedCornerShape(topStart = 0f, bottomStart = 0f, topEnd = 50f, bottomEnd = 50f)
                }
            )
            // Support both click and drag with consistent visual feedback
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { 
                        isPressed = true
                        isHovered = true
                        
                        // This is critical - immediately start connection on press
                        println("CONNECTION POINT: Starting connection - Press detected on $type")
                        onConnectionStart()
                        
                        // Wait for release
                        if (tryAwaitRelease()) {
                            println("CONNECTION POINT: Released on $type")
                        }
                        
                        isPressed = false
                        isHovered = false
                    }
                )
            }
            // Also track hover state for visual feedback
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Check if the pointer is over this connection point
                        val isPointerOver = event.changes.any { 
                            it.position.x >= 0 && it.position.y >= 0 &&
                            it.position.x <= size.width && it.position.y <= size.height
                        }
                        
                        // Update hover state based on pointer position
                        if (isPointerOver != isHovered && !isPressed) {
                            isHovered = isPointerOver
                        }
                    }
                }
            }
    )
}

@Composable
fun connectionCanvas(
    connections: List<Connection>,
    dragState: ConnectionDragState,
    blocks: List<Block>,
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
            println("Drawing active connection: ${dragState.sourceBlock!!.content} from ${dragState.sourcePointType} to ${dragState.currentPosition}")
            
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
            
            // Draw little circle at current drag position for debugging
            drawCircle(
                color = Color.Blue.copy(alpha = 0.5f),
                radius = 5f,
                center = targetPoint
            )
            
            // Highlight potential connection points
            blocks.forEach { block ->
                // Skip the block we're dragging from
                if (block.id != dragState.sourceBlock!!.id) {
                    val connectablePointType = if (dragState.sourcePointType == ConnectionPointType.OUTPUT) {
                        ConnectionPointType.INPUT
                    } else {
                        ConnectionPointType.OUTPUT
                    }
                    
                    val connectablePoint = block.getConnectionPointPosition(connectablePointType)
                    val distance = (dragState.currentPosition - connectablePoint).getDistance()
                    
                    // If we're close to a valid connection point, highlight it
                    if (distance < 30f) {
                        val pointPx = dpToPx(connectablePoint)
                        drawCircle(
                            color = if (connectablePointType == ConnectionPointType.INPUT) 
                                Color.Green.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.5f),
                            radius = 15f,
                            center = pointPx
                        )
                        
                        // Draw dotted guide line to help with alignment
                        val dashLength = 5f
                        val gapLength = 5f
                        val totalLength = (pointPx - targetPoint).getDistance()
                        val angle = atan2(pointPx.y - targetPoint.y, pointPx.x - targetPoint.x)
                        
                        var distance = 0f
                        while (distance < totalLength) {
                            val startX = targetPoint.x + cos(angle) * distance
                            val startY = targetPoint.y + sin(angle) * distance
                            val endX = targetPoint.x + cos(angle) * (distance + dashLength).coerceAtMost(totalLength)
                            val endY = targetPoint.y + sin(angle) * (distance + dashLength).coerceAtMost(totalLength)
                            
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 1f,
                                cap = StrokeCap.Round
                            )
                            
                            distance += dashLength + gapLength
                        }
                    }
                }
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

// Property Editor Panel Composable
@Composable
fun propertyEditorPanel(
    block: Block,
    onPropertyChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    println("PROPERTY-PANEL: Showing properties for block ${block.id} with content '${block.content}'")
    
    val blockContent = block.content
    
    val properties = TerraformProperties.getPropertiesForBlock(block)
    
    val blockKey = remember(block.id) { block.id }
    
    Card(
        modifier = modifier
            .widthIn(min = 320.dp, max = 400.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            // Title with resource icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Resource type icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = when (block.type) {
                                BlockType.COMPUTE -> Color(0xFF4C97FF)
                                BlockType.DATABASE -> Color(0xFFFFAB19)
                                BlockType.NETWORKING -> Color(0xFFFF8C1A)
                                BlockType.SECURITY -> Color(0xFF40BF4A)
                                BlockType.INTEGRATION -> Color(0xFF4C97FF)
                                BlockType.MONITORING -> Color(0xFFFFAB19)
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "$blockContent properties",
                    style = MaterialTheme.typography.h6
                )
            }
            
            if (properties.isEmpty()) {
                Text(
                    text = "No properties available for $blockContent.",
                    style = MaterialTheme.typography.body2
                )
            } else {
                // For each property, create an appropriate editor based on type
                properties.forEach { property ->
                    val currentValue = block.getProperty(property.name) ?: property.default ?: ""
                    
                    // Property section with label and field
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // Property name with required indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${property.name}${if (property.required) " *" else ""}",
                                style = MaterialTheme.typography.subtitle2
                            )
                            
                            if (property.required) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(required)",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.primary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Property editor based on type
                        when (property.type) {
                            PropertyType.STRING -> {
                                // Use a key combining block ID and property name to reset state
                                key(blockKey, property.name) {
                                    var value by remember { mutableStateOf(currentValue) }
                                    
                                    // Update value when currentValue changes (e.g., from outside)
                                    LaunchedEffect(currentValue) {
                                        value = currentValue
                                    }
                                    
                                    OutlinedTextField(
                                        value = value,
                                        onValueChange = { 
                                            value = it
                                            onPropertyChange(property.name, it)
                                        },
                                        label = { Text(property.description) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                            
                            PropertyType.NUMBER -> {
                                // Use a key combining block ID and property name to reset state
                                key(blockKey, property.name) {
                                    var value by remember { mutableStateOf(currentValue) }
                                    
                                    // Update value when currentValue changes (e.g., from outside)
                                    LaunchedEffect(currentValue) {
                                        value = currentValue
                                    }
                                    
                                    OutlinedTextField(
                                        value = value,
                                        onValueChange = { newValue ->
                                            // Only allow numbers
                                            if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '.' }) {
                                                value = newValue
                                                onPropertyChange(property.name, newValue)
                                            }
                                        },
                                        label = { Text(property.description) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                            
                            PropertyType.BOOLEAN -> {
                                // Use a key combining block ID and property name to reset state
                                key(blockKey, property.name) {
                                    var checked by remember { mutableStateOf(currentValue.lowercase(Locale.getDefault()) == "true") }
                                    
                                    // Update checked state when currentValue changes (e.g., from outside)
                                    LaunchedEffect(currentValue) {
                                        checked = currentValue.lowercase(Locale.getDefault()) == "true"
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Switch(
                                            checked = checked,
                                            onCheckedChange = { 
                                                checked = it
                                                onPropertyChange(property.name, it.toString())
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colors.primary
                                            )
                                        )
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Text(
                                            text = property.description,
                                            style = MaterialTheme.typography.body2
                                        )
                                    }
                                }
                            }
                            
                            PropertyType.ENUM -> {
                                // Use a key combining block ID and property name to reset state
                                key(blockKey, property.name) {
                                    // State for dropdown
                                    var expanded by remember { mutableStateOf(false) }
                                    var selectedOption by remember { mutableStateOf(currentValue) }
                                    
                                    // Update selected option when currentValue changes (e.g., from outside)
                                    LaunchedEffect(currentValue) {
                                        selectedOption = currentValue
                                    }
                                    
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = selectedOption,
                                            onValueChange = { },
                                            label = { Text(property.description) },
                                            readOnly = true,
                                            trailingIcon = {
                                                IconButton(onClick = { expanded = !expanded }) {
                                                    Icon(
                                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = if (expanded) "Collapse" else "Expand"
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expanded = true }
                                        )
                                        
                                        // Dropdown menu for enum values
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                        ) {
                                            property.options.forEach { option ->
                                                DropdownMenuItem(
                                                    onClick = {
                                                        selectedOption = option
                                                        expanded = false
                                                        onPropertyChange(property.name, option)
                                                    }
                                                ) {
                                                    Text(text = option)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (properties.last() != property) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}