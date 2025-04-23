package terraformbuilder.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EditorViewState {
    // The current view mode
    enum class Mode { MAIN, COMPOSITE }

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
        selectedBlockId = blockId
        selectedCompositeId = null
    }

    fun selectComposite(compositeId: String?) {
        selectedCompositeId = compositeId
        selectedBlockId = null
    }

    fun setNoneSelected() {
        selectedBlockId = null
        selectedCompositeId = null
    }

    // Helper for UI to determine what's visible
    fun isInMainView() = mode == Mode.MAIN

    fun isInCompositeView() = mode == Mode.COMPOSITE
}