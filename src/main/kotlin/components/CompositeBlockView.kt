package terraformbuilder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun compositeBlockView(
    compositeBlock: CompositeBlock,
    onDragStart: () -> Unit,
    onDragEnd: (Offset) -> Unit,
    onRename: (String) -> Unit,
    onBlockSelected: (String) -> Unit = {},
    isSelected: Boolean = false
) {
    var position by remember { mutableStateOf(compositeBlock.position) }
    val blockSize = 100.dp // Fixed size for composite blocks

    // Function to resolve the icon from string name
    val icon = remember(compositeBlock.iconCode) {
        try {
            // Try to find the icon in Icons.Filled
            val iconClass = Icons.Filled::class.java
            val field = iconClass.getDeclaredField(compositeBlock.iconCode)
            field.isAccessible = true
            field.get(Icons.Filled) as ImageVector
        } catch (e: Exception) {
            // Fallback to a default icon
            Icons.Default.Category
        }
    }

    // For circular representation
    Box(
        modifier = Modifier
            .offset(position.x.dp, position.y.dp)
            .size(blockSize)
            .border(
                width = if (isSelected) 3.dp else 2.dp,
                color = if (isSelected) MaterialTheme.colors.primary else Color.Gray,
                shape = CircleShape
            )
            .background(
                color = compositeBlock.color,
                shape = CircleShape
            )
            .clickable { onBlockSelected(compositeBlock.id) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd(position) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        position += dragAmount.toDpOffset(density)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Dynamic icon
            Icon(
                imageVector = icon,
                contentDescription = compositeBlock.name,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )

            Spacer(Modifier.height(4.dp))

            // Block name
            Text(
                text = compositeBlock.name,
                color = Color.White,
                style = MaterialTheme.typography.subtitle2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}