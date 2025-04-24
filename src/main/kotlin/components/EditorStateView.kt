package terraformbuilder.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

class EditorViewState {
    // The current view mode
    enum class Mode { MAIN, COMPOSITE }
    enum class DialogType {
        TEMPLATE, GROUP, GITHUB, LOCAL_DIRECTORY, VARIABLES, TERRAFORM_GENERATION
    }

    // Current view state
    var mode by mutableStateOf(Mode.MAIN)
        private set

    // ID of the composite being edited (if any)
    var activeCompositeId by mutableStateOf<String?>(null)
        private set

    // Selection state
    var selectedBlockId by mutableStateOf<String?>(null)
        private set

    var selectedCompositeId by mutableStateOf<String?>(null)
        private set

    var selectedBlockIds by mutableStateOf<List<String>>(emptyList())
        private set

    // Pan and zoom state
    var panOffset by mutableStateOf(Offset.Zero)
        private set
    var scale by mutableStateOf(1f)
        private set

    // Drag state
    var isDragging by mutableStateOf(false)
        private set
    var isPanning by mutableStateOf(false)
        private set
    var draggedBlockId by mutableStateOf<String?>(null)
        private set
    var dragStartPosition by mutableStateOf<Offset?>(null)
        private set
    var dragStartBlockId by mutableStateOf<String?>(null)
        private set
    var lastPanPosition by mutableStateOf(Offset.Zero)
        private set
    var isMouseDown by mutableStateOf(false)
        private set

    // Dialog state
    var showTemplateDialog by mutableStateOf(false)
        private set
    var showGroupDialog by mutableStateOf(false)
        private set
    var showGithubDialog by mutableStateOf(false)
        private set
    var showLocalDirectoryDialog by mutableStateOf(false)
        private set
    var showVariablesDialog by mutableStateOf(false)
        private set
    var templateName by mutableStateOf("")
        private set
    var groupNameInput by mutableStateOf("New Group")
        private set
    var selectedTemplateName by mutableStateOf("")
        private set

    // Navigation functions
    fun enterComposite(compositeId: String) {
        activeCompositeId = compositeId
        mode = Mode.COMPOSITE
        // Clear selections when changing view
        setNoneSelected()
    }

    fun exitToMainView() {
        mode = Mode.MAIN
        activeCompositeId = null
        // Clear selections when changing view
        setNoneSelected()
    }

    // Selection functions
    fun selectBlock(blockId: String?) {
        setNoneSelected()
        selectedBlockId = blockId
    }

    fun selectComposite(compositeId: String?) {
        setNoneSelected()
        selectedCompositeId = compositeId
    }

    fun setNoneSelected() {
        selectedBlockId = null
        selectedCompositeId = null
    }

    // Multi-selection functions
    fun toggleBlockSelection(blockId: String) {
        selectedBlockIds = if (selectedBlockIds.contains(blockId)) {
            selectedBlockIds.filter { it != blockId }
        } else {
            selectedBlockIds + blockId
        }
    }

    fun clearMultiSelection() {
        selectedBlockIds = emptyList()
    }

    // Pan and zoom functions
    fun pan(offset: Offset) {
        panOffset += offset
    }

    fun zoom(factor: Float, focusPoint: Offset? = null) {
        // Implement zooming logic with focus point if provided
        val oldScale = scale
        scale = scale * factor

        // If focus point is provided, adjust pan to keep that point stationary
        if (focusPoint != null) {
            val scaleFactor = scale / oldScale
            val focusPointInWorld = (focusPoint - panOffset) / oldScale
            val newFocusPointInWorld = focusPointInWorld * scaleFactor
            panOffset = focusPoint - newFocusPointInWorld
        }
    }

    fun centerOn(position: Offset, viewportSize: Offset) {
        panOffset = -position + (viewportSize / 2f) / scale
    }

    fun centerOnBlocks(blocks: List<Block>, viewportSize: Size) {
        if (blocks.isEmpty()) return

        // Find the center of all blocks
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        blocks.forEach { block ->
            val left = block.position.x
            val top = block.position.y
            val right = left + block.size.x
            val bottom = top + block.size.y

            minX = minOf(minX, left)
            minY = minOf(minY, top)
            maxX = maxOf(maxX, right)
            maxY = maxOf(maxY, bottom)
        }

        // Center the view on the blocks
        if (minX != Float.MAX_VALUE) {
            val centerX = (minX + maxX) / 2
            val centerY = (minY + maxY) / 2

            // Set pan to center blocks
            panOffset = Offset(
                -centerX + viewportSize.width / 2,
                -centerY + viewportSize.height / 2
            )
        }
    }

    // Drag state management
    fun startDrag(blockId: String?, position: Offset) {
        draggedBlockId = blockId
        dragStartPosition = position
        dragStartBlockId = blockId
        isDragging = false
        isMouseDown = true
    }

    fun startPanning(position: Offset) {
        isPanning = true
        isMouseDown = true
        lastPanPosition = position
        draggedBlockId = null
        dragStartBlockId = null
    }

    fun startMouseDown() {
        isMouseDown = true
    }

    fun updateDrag(position: Offset, dragAmount: Offset) {
        isDragging = true
        dragStartPosition = position
    }

    fun updatePan(dragAmount: Offset) {
        panOffset += dragAmount
    }

    fun endDrag() {
        isMouseDown = false
        isPanning = false
        isDragging = false
        draggedBlockId = null
        dragStartPosition = null
    }

    fun cancelDrag() {
        isMouseDown = false
        isPanning = false
        isDragging = false
        draggedBlockId = null
        dragStartPosition = null
        dragStartBlockId = null
    }

    // Coordinate transformations
    fun screenToWorkspace(screenPos: Offset, density: Float): Offset {
        // First convert to dp
        val dpPos = screenPos / density
        // Then apply inverse transform (pan and scale)
        return (dpPos - panOffset) / scale
    }

    // Dialog state management
    fun showDialog(dialogType: DialogType) {
        when (dialogType) {
            DialogType.TEMPLATE -> showTemplateDialog = true
            DialogType.GROUP -> showGroupDialog = true
            DialogType.GITHUB -> showGithubDialog = true
            DialogType.LOCAL_DIRECTORY -> showLocalDirectoryDialog = true
            DialogType.VARIABLES -> showVariablesDialog = true
            DialogType.TERRAFORM_GENERATION -> {} // Handled separately
        }
    }

    fun hideDialog(dialogType: DialogType) {
        when (dialogType) {
            DialogType.TEMPLATE -> showTemplateDialog = false
            DialogType.GROUP -> showGroupDialog = false
            DialogType.GITHUB -> showGithubDialog = false
            DialogType.LOCAL_DIRECTORY -> showLocalDirectoryDialog = false
            DialogType.VARIABLES -> showVariablesDialog = false
            DialogType.TERRAFORM_GENERATION -> {} // Handled separately
        }
    }

    fun setTemplateSelection(name: String) {
        selectedTemplateName = name
        templateName = name
    }

    fun updateTemplateName(name: String) {
        templateName = name
    }

    fun updateGroupName(name: String) {
        groupNameInput = name
    }

    // Helper for UI to determine what's visible
    fun isInMainView() = mode == Mode.MAIN
    fun isInCompositeView() = mode == Mode.COMPOSITE
}