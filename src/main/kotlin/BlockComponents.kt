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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/**
 * Represents a property for a Terraform resource
 */
data class TerraformProperty(
    val name: String,
    val type: PropertyType,
    val default: String? = null,
    val required: Boolean = false,
    val description: String = "",
    val options: List<String> = emptyList() // For enum types
)

enum class PropertyType {
    STRING, NUMBER, BOOLEAN, ENUM
}

// Map of block types to their available properties
object TerraformProperties {
    val blockTypeProperties = mapOf(
        // Compute resources
        Pair("Lambda Function", listOf(
            TerraformProperty("function_name", PropertyType.STRING, required = true, description = "Name of the Lambda function"),
            TerraformProperty("runtime", PropertyType.ENUM, default = "nodejs18.x", required = true, 
                description = "Runtime environment", 
                options = listOf("nodejs18.x", "nodejs16.x", "python3.9", "python3.8", "java11", "go1.x", "ruby2.7")),
            TerraformProperty("handler", PropertyType.STRING, default = "index.handler", required = true, 
                description = "Function entry point"),
            TerraformProperty("memory_size", PropertyType.NUMBER, default = "128", description = "Memory allocation in MB"),
            TerraformProperty("timeout", PropertyType.NUMBER, default = "3", description = "Timeout in seconds"),
            TerraformProperty("publish", PropertyType.BOOLEAN, default = "false", description = "Publish new version")
        )),
        Pair("EC2 Instance", listOf(
            TerraformProperty("instance_type", PropertyType.STRING, default = "t2.micro", required = true, description = "EC2 instance type"),
            TerraformProperty("ami", PropertyType.STRING, required = true, description = "AMI ID to use for the instance"),
            TerraformProperty("key_name", PropertyType.STRING, description = "Key pair name for SSH access"),
            TerraformProperty("monitoring", PropertyType.BOOLEAN, default = "false", description = "Enable detailed monitoring")
        )),
        
        // Database resources
        Pair("DynamoDB Table", listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the DynamoDB table"),
            TerraformProperty("billing_mode", PropertyType.ENUM, default = "PROVISIONED", 
                options = listOf("PROVISIONED", "PAY_PER_REQUEST"), 
                description = "Controls how you are charged for read and write throughput"),
            TerraformProperty("read_capacity", PropertyType.NUMBER, default = "5", description = "Read capacity units"),
            TerraformProperty("write_capacity", PropertyType.NUMBER, default = "5", description = "Write capacity units")
        )),
        Pair("RDS Instance", listOf(
            TerraformProperty("allocated_storage", PropertyType.NUMBER, default = "10", required = true, description = "Allocated storage in gigabytes"),
            TerraformProperty("engine", PropertyType.ENUM, required = true, 
                options = listOf("mysql", "postgres", "mariadb", "oracle-ee", "sqlserver-ee"),
                description = "Database engine"),
            TerraformProperty("instance_class", PropertyType.STRING, default = "db.t3.micro", required = true, description = "Database instance type"),
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the database"),
            TerraformProperty("username", PropertyType.STRING, required = true, description = "Master username"),
            TerraformProperty("password", PropertyType.STRING, required = true, description = "Master password"),
            TerraformProperty("skip_final_snapshot", PropertyType.BOOLEAN, default = "true", description = "Skip final snapshot before deletion")
        )),
        Pair("S3 Bucket", listOf(
            TerraformProperty("bucket", PropertyType.STRING, description = "Bucket name (if not specified, a random name will be used)"),
            TerraformProperty("acl", PropertyType.ENUM, default = "private", 
                options = listOf("private", "public-read", "public-read-write", "authenticated-read"),
                description = "Canned ACL for the bucket"),
            TerraformProperty("versioning_enabled", PropertyType.BOOLEAN, default = "false", description = "Enable versioning"),
            TerraformProperty("force_destroy", PropertyType.BOOLEAN, default = "false", description = "Allow deletion of non-empty bucket")
        )),
        
        // Networking resources
        Pair("VPC", listOf(
            TerraformProperty("cidr_block", PropertyType.STRING, required = true, default = "10.0.0.0/16", description = "CIDR block for the VPC"),
            TerraformProperty("enable_dns_support", PropertyType.BOOLEAN, default = "true", description = "Enable DNS support"),
            TerraformProperty("enable_dns_hostnames", PropertyType.BOOLEAN, default = "false", description = "Enable DNS hostnames")
        )),
        Pair("Security Group", listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the security group"),
            TerraformProperty("description", PropertyType.STRING, default = "Managed by Terraform", description = "Description of the security group"),
            TerraformProperty("vpc_id", PropertyType.STRING, required = true, description = "VPC ID")
        )),
        
        // Security resources
        Pair("IAM Role", listOf(
            TerraformProperty("name", PropertyType.STRING, required = true, description = "Name of the IAM role"),
            TerraformProperty("description", PropertyType.STRING, description = "Description of the IAM role"),
            TerraformProperty("assume_role_policy", PropertyType.STRING, required = true, description = "Policy that grants an entity permission to assume the role")
        )),
        Pair("KMS Key", listOf(
            TerraformProperty("description", PropertyType.STRING, description = "Description of the KMS key"),
            TerraformProperty("deletion_window_in_days", PropertyType.NUMBER, default = "10", description = "Duration in days after which the key is deleted"),
            TerraformProperty("enable_key_rotation", PropertyType.BOOLEAN, default = "false", description = "Enable automatic key rotation")
        ))
    )
    
    // Helper method to get properties for a given block
    fun getPropertiesForBlock(block: Block): List<TerraformProperty> {
        return blockTypeProperties[block.content] ?: emptyList()
    }
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
}

// Block creation helper
fun createBlock(
    id: String,
    type: BlockType,
    content: String,
): Block {
    val block = Block(
        id = id,
        type = type,
        content = content
    )
    
    // Initialize default properties
    block.initializeDefaultProperties()
    
    return block
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
                    println("BLOCK: Starting INPUT connection for block ${block.content}")
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
                    println("BLOCK: Starting OUTPUT connection for block ${block.content}")
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
    var isHovered by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) } 
    
    Box(
        modifier = Modifier
            .size(20.dp) // Larger size for easier interaction
            .background(
                color = when (type) {
                    ConnectionPointType.INPUT -> if (isPressed) Color.Green else if (isHovered) Color.Green.copy(alpha = 0.9f) else Color.Green.copy(alpha = 0.7f)
                    ConnectionPointType.OUTPUT -> if (isPressed) Color.Red else if (isHovered) Color.Red.copy(alpha = 0.9f) else Color.Red.copy(alpha = 0.7f)
                },
                shape = CircleShape
            )
            .border(
                width = if (isPressed || isHovered) 3.dp else 2.dp,
                color = Color.Black,
                shape = CircleShape
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

// Connections Canvas
@Composable
fun ConnectionsCanvas(
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
fun PropertyEditorPanel(
    block: Block,
    onPropertyChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val properties = TerraformProperties.getPropertiesForBlock(block)
    
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
                    text = "Edit ${block.content}",
                    style = MaterialTheme.typography.h6
                )
            }
            
            if (properties.isEmpty()) {
                Text(
                    text = "No editable properties available for this resource type.",
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
                                var value by remember { mutableStateOf(currentValue) }
                                
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
                            
                            PropertyType.NUMBER -> {
                                var value by remember { mutableStateOf(currentValue) }
                                
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
                            
                            PropertyType.BOOLEAN -> {
                                var checked by remember { mutableStateOf(currentValue.toLowerCase() == "true") }
                                
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
                            
                            PropertyType.ENUM -> {
                                // State for dropdown
                                var expanded by remember { mutableStateOf(false) }
                                var selectedOption by remember { mutableStateOf(currentValue) }
                                
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
                    
                    if (properties.last() != property) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}