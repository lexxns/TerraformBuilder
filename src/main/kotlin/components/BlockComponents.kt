package terraformbuilder.components

// Add new imports
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import terraformbuilder.ResourceType
import terraformbuilder.terraform.TerraformProperties
import terraformbuilder.terraform.TerraformReferenceAnalyzer
import terraformbuilder.utils.OffsetSerializer
import java.util.*
import kotlin.math.*

@Serializable
enum class ConnectionPointType {
    INPUT, OUTPUT
}

/**
 * Extension functions to convert between Dp and pixels when needed
 */
fun Offset.toDpOffset(density: Float): Offset {
    return Offset(x / density, y / density)
}

@Serializable
data class Block(
    val id: String,
    val type: BlockType,
    var content: String,
    val resourceType: ResourceType,
    val description: String = "",
    @Serializable(with = OffsetSerializer::class)
    var _position: Offset = Offset.Zero,
    @Serializable(with = OffsetSerializer::class)
    var _size: Offset = Offset(120f, 40f),
    @Serializable(with = OffsetSerializer::class)
    var inputPosition: Offset = Offset.Zero,  // Position of the input connection point in dp
    @Serializable(with = OffsetSerializer::class)
    var outputPosition: Offset = Offset.Zero,  // Position of the output connection point in dp
    @Serializable
    val properties: MutableMap<String, String> = mutableMapOf()
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
        // For input: left edge, vertically centered
        // Position it slightly to the left to appear as part of the block
        inputPosition = _position + Offset(-6f, _size.y / 2f)

        // For output: right edge, vertically centered
        // Position it slightly to the right to appear as part of the block
        outputPosition = _position + Offset(_size.x + 6f, _size.y / 2f)
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

    // Initialize connection points after deserialization
    init {
        updateConnectionPoints()
    }
}

@Serializable
enum class BlockType {
    LOAD_BALANCER,  // ALB, ELB, Target Groups, etc.
    EC2,           // EC2 instances, launch templates, etc.
    VPC,           // VPC, subnets, route tables, etc.
    SECURITY,      // IAM, KMS, Security Groups, etc.
    LAMBDA,        // Lambda functions, layers, etc.
    ECS,           // ECS clusters, services, tasks, etc.
    RDS,           // RDS instances, clusters, etc.
    DYNAMODB,      // DynamoDB tables, items, etc.
    STORAGE,       // S3 buckets, policies, etc.
    MONITORING,    // CloudWatch, alarms, logs, etc.
    API_GATEWAY,   // API Gateway resources, methods, etc.
    SQS,           // SQS queues, policies, etc.
    SNS,           // SNS topics, subscriptions, etc.
    KINESIS,       // Kinesis streams, firehose, etc.
    INTEGRATION    // Default for unknown types
}

// State for tracking active connection being drawn
@Serializable
class ConnectionDragState {
    var isActive = false
    var sourceBlock: Block? = null
    var sourcePointType: ConnectionPointType? = null

    @Serializable(with = OffsetSerializer::class)
    var currentPosition = Offset.Zero  // in dp
}

// Connection class with direct references to blocks
@Serializable
data class Connection(
    val id: String = UUID.randomUUID().toString(),
    var sourceBlock: Block,
    var targetBlock: Block
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

    // Initialize connection points after deserialization
    init {
        // Ensure connection points are up to date
        sourceBlock.updateConnectionPoints()
        targetBlock.updateConnectionPoints()
    }
}

// Block state management
@Serializable
class BlockState {
    private var _blocks = mutableListOf<Block>()
    val blocks: List<Block> get() = if (currentCompositeId == null) _blocks else emptyList()

    private var _compositeBlocks = mutableListOf<CompositeBlock>()
    val compositeBlocks: List<CompositeBlock> get() = if (currentCompositeId == null) _compositeBlocks else emptyList()

    // Track which composite block we're currently "inside" - null means we're at the root level
    private var currentCompositeId: String? = null

    private var _connections = mutableListOf<Connection>()
    val connections: List<Connection> = _connections

    val dragState = ConnectionDragState()

    fun addCompositeBlock(compositeBlock: CompositeBlock) {
        _compositeBlocks.add(compositeBlock)
    }

    // Child blocks of the current composite being viewed
    val currentCompositeChildren: List<Block>
        get() = if (currentCompositeId != null) {
            _compositeBlocks.find { it.id == currentCompositeId }?.children ?: emptyList()
        } else {
            emptyList()
        }

    // Enter a composite block to view/edit its children
    fun enterComposite(compositeId: String) {
        if (_compositeBlocks.any { it.id == compositeId }) {
            currentCompositeId = compositeId
        }
    }

    // Exit back to the main view
    fun exitComposite() {
        currentCompositeId = null
    }

    // Add a pre-configured composite
    fun addRestApiComposite(name: String, position: Offset): CompositeBlock {
        val composite = CompositeBlockFactory.createRestApi(name, position)
        _compositeBlocks.add(composite)
        return composite
    }

    fun addVpcNetworkComposite(name: String, position: Offset): CompositeBlock {
        val composite = CompositeBlockFactory.createVpcNetwork(name, position)
        _compositeBlocks.add(composite)
        return composite
    }

    fun addServerlessBackendComposite(name: String, position: Offset): CompositeBlock {
        val composite = CompositeBlockFactory.createServerlessBackend(name, position)
        _compositeBlocks.add(composite)
        return composite
    }

    fun addDatabaseClusterComposite(name: String, position: Offset): CompositeBlock {
        val composite = CompositeBlockFactory.createDatabaseCluster(name, position)
        _compositeBlocks.add(composite)
        return composite
    }

    // Group existing blocks into a new composite
    fun groupBlocks(blockIds: List<String>, name: String): CompositeBlock? {
        // Find the blocks to group
        val blocksToGroup = _blocks.filter { it.id in blockIds }
        if (blocksToGroup.isEmpty()) return null

        // Create a new empty composite
        val composite = CompositeBlockFactory.createCustomGroup(name, Offset.Zero)

        // Calculate position based on blocks
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE

        blocksToGroup.forEach { block ->
            minX = minOf(minX, block.position.x)
            minY = minOf(minY, block.position.y)
        }

        composite.position = Offset(minX, minY)

        // Add blocks to composite
        composite.children.addAll(blocksToGroup)

        // Remove blocks from main list
        _blocks.removeAll { it.id in blockIds }

        // Add composite to list
        _compositeBlocks.add(composite)

        return composite
    }

    // Ungroup a composite
    fun ungroupComposite(compositeId: String) {
        val composite = _compositeBlocks.find { it.id == compositeId } ?: return

        // Add all children back to main list
        _blocks.addAll(composite.children)

        // Remove the composite
        _compositeBlocks.remove(composite)

        // If we were viewing this composite, exit it
        if (currentCompositeId == compositeId) {
            currentCompositeId = null
        }
    }

    fun addBlock(block: Block) {
        // Initialize default properties when adding a block
        block.initializeDefaultProperties()
        // Ensure connection points are up to date
        block.updateConnectionPoints()
        _blocks.add(block)
    }

    fun restoreConnections(connections: List<Connection>) {
        _connections.clear()
        _connections.addAll(connections)
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

    // Check if a block is referenced by any other block's properties
    fun isBlockReferenced(blockId: String): Boolean {
        val block = _blocks.find { it.id == blockId } ?: return false
        val resourceType = block.resourceType.resourceName
        val resourceName = formatResourceName(block.content)
        val referenceAnalyzer = TerraformReferenceAnalyzer()

        return _blocks.any { otherBlock ->
            if (otherBlock.id == blockId) return@any false

            // Use the analyzer to check each property
            otherBlock.properties.values.any { propertyValue ->
                // Find all resource references in this property
                val references = referenceAnalyzer.findResourceReferences(propertyValue)

                // Check if any reference matches our block
                references.any { (refType, refName) ->
                    refType == resourceType && refName == resourceName
                }
            }
        }
    }

    // Helper to format resource name for reference checking
    private fun formatResourceName(content: String): String {
        // Convert to valid Terraform resource name: lowercase, underscores, no special chars
        return content.lowercase().replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
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
        // Initialize a new drag state
        dragState.isActive = true
        dragState.sourceBlock = block
        dragState.sourcePointType = pointType

        // Set the initial position to the connection point's position
        val connectionPointPosition = block.getConnectionPointPosition(pointType)
        dragState.currentPosition = connectionPointPosition
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

            println(
                "Block ${block.content} valid? " +
                        "different block: $validBlock, " +
                        "nearby: $validDistance, " +
                        "compatible types: $compatibleTypes"
            )

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
    private fun findNearestConnectionPoint(
        block: Block,
        position: Offset
    ): Triple<Boolean, ConnectionPointType, Float> {
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
    resourceType: ResourceType,
    description: String = ""
): Block {
    val block = Block(
        id = id,
        type = type,
        content = content,
        resourceType = resourceType,
        description = description
    )

    // Initialize default properties
    block.initializeDefaultProperties()

    return block
}

/**
 * Centralized color mappings for BlockTypes
 */
object BlockTypeColors {
    val colors = mapOf(
        BlockType.LOAD_BALANCER to Color(0xFF4C97FF),  // Blue
        BlockType.EC2 to Color(0xFFFF8C1A),           // Orange
        BlockType.VPC to Color(0xFF40BF4A),           // Green
        BlockType.SECURITY to Color(0xFFFFAB19),      // Yellow
        BlockType.LAMBDA to Color(0xFF4C97FF),        // Blue
        BlockType.ECS to Color(0xFFFF8C1A),          // Orange
        BlockType.RDS to Color(0xFF40BF4A),          // Green
        BlockType.DYNAMODB to Color(0xFFFFAB19),     // Yellow
        BlockType.STORAGE to Color(0xFF4C97FF),      // Blue
        BlockType.MONITORING to Color(0xFFFF8C1A),   // Orange
        BlockType.API_GATEWAY to Color(0xFF40BF4A),  // Green
        BlockType.SQS to Color(0xFFFFAB19),         // Yellow
        BlockType.SNS to Color(0xFF4C97FF),         // Blue
        BlockType.KINESIS to Color(0xFFFF8C1A),     // Orange
        BlockType.INTEGRATION to Color(0xFF808080)  // Gray
    )

    fun getColor(type: BlockType): Color = colors[type] ?: Color(0xFF808080)
}

// UI Components

@Composable
fun blockView(
    block: Block,
    onDragStart: () -> Unit,
    onDragEnd: (Offset) -> Unit,
    onRename: (String) -> Unit,
    onConnectionDragStart: (Block, ConnectionPointType) -> Unit,
    onUpdateBlockSize: (String, Offset) -> Unit,
    onBlockSelected: (String) -> Unit = {},
    isHovered: Boolean = false,
    isReferenced: Boolean = false,
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

    // Set up reference highlighting animation
    val referenceAlpha by animateFloatAsState(
        targetValue = if (isReferenced) 1f else 0f,
        label = "referenceHighlight"
    )

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
        // Add a glow effect when referenced
        if (isReferenced) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp)
                    .background(
                        color = Color(0xFFFFA500).copy(alpha = 0.2f * referenceAlpha),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .zIndex(0.5f)
            )
        }
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
                    color = BlockTypeColors.getColor(block.type),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = if (isReferenced) 3.dp else 2.dp,
                    color = if (isReferenced) Color(0xFFFFA500) else Color.Black, // Orange highlight when referenced
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
                            if (isEditing) {
                                isEditing = false
                                onRename(textFieldValue.text)
                            }
                            onDragStart()
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
                    style = MaterialTheme.typography.body1.copy(
                        fontWeight = if (isReferenced) FontWeight.Bold
                        else FontWeight.Normal
                    )
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
                    ConnectionPointType.INPUT -> RoundedCornerShape(
                        topStart = 50f,
                        bottomStart = 50f,
                        topEnd = 0f,
                        bottomEnd = 0f
                    )
                    // Half circle for output (flat side on left)
                    ConnectionPointType.OUTPUT -> RoundedCornerShape(
                        topStart = 0f,
                        bottomStart = 0f,
                        topEnd = 50f,
                        bottomEnd = 50f
                    )
                }
            )
            .border(
                width = if (isPressed || isHovered) 2.dp else 1.dp,
                color = Color.Black,
                shape = when (type) {
                    // Half circle for input (flat side on right)
                    ConnectionPointType.INPUT -> RoundedCornerShape(
                        topStart = 50f,
                        bottomStart = 50f,
                        topEnd = 0f,
                        bottomEnd = 0f
                    )
                    // Half circle for output (flat side on left)
                    ConnectionPointType.OUTPUT -> RoundedCornerShape(
                        topStart = 0f,
                        bottomStart = 0f,
                        topEnd = 50f,
                        bottomEnd = 50f
                    )
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
                        color = BlockTypeColors.getColor(block.type),
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

