package terraformbuilder.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import terraformbuilder.utils.ColorSerializer
import terraformbuilder.utils.OffsetSerializer
import java.util.*

@Serializable
data class CompositeBlock(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var description: String = "",
    @Serializable(with = OffsetSerializer::class)
    var _position: Offset = Offset.Zero,
    // Icon code for rendering - allows any Material icon
    var iconCode: String = "Category", // Default icon
    // Background color
    @Serializable(with = ColorSerializer::class)
    var color: Color = Color(0xFF9E9E9E), // Default gray
    val children: MutableList<Block> = mutableListOf(),
    val properties: MutableMap<String, String> = mutableMapOf()
) {
    // Position handling and other properties like before
    var position: Offset
        get() = _position
        set(value) {
            // Calculate delta from old position
            val delta = value - _position
            _position = value

            // Move all children by the same delta
            children.forEach { child ->
                child.position += delta
            }
        }

    // Add a child block
    fun addChild(block: Block) {
        children.add(block)
    }

    // Remove a child block
    fun removeChild(blockId: String): Block? {
        val child = children.find { it.id == blockId } ?: return null
        children.remove(child)
        return child
    }

    // Get a property value or default
    fun getProperty(name: String): String? {
        return properties[name]
    }

    // Set a property value
    fun setProperty(name: String, value: String) {
        properties[name] = value
    }
}

