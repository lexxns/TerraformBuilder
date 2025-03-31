package terraformbuilder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import terraformbuilder.Block
import terraformbuilder.BlockType
import terraformbuilder.terraform.PropertyType
import terraformbuilder.terraform.TerraformProperties
import terraformbuilder.terraform.TerraformProperty
import java.util.*

@Composable
private fun jsonPropertyEditor(
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentValue) }
    var jsonError by remember { mutableStateOf<String?>(null) }

    // Update text when currentValue changes from outside
    LaunchedEffect(currentValue) {
        if (currentValue != text) {
            text = currentValue
        }
    }

    // Validate JSON when text changes
    LaunchedEffect(text) {
        try {
            if (text.isBlank()) {
                jsonError = null
                onValueChange("{}")
                return@LaunchedEffect
            }

            // Try to parse as JSON to validate
            org.json.JSONObject(text)
            jsonError = null
            onValueChange(text)
        } catch (e: Exception) {
            jsonError = "Invalid JSON: ${e.message}"
        }
    }

    // Create a visual transformation for JSON syntax highlighting
    val jsonSyntaxHighlighting = VisualTransformation { inputText ->
        val annotatedString = buildAnnotatedString {
            var currentIndex = 0

            while (currentIndex < inputText.text.length) {
                val char = inputText.text[currentIndex]

                when {
                    // Handle strings
                    char == '"' -> {
                        val endIndex = findClosingQuote(inputText.text, currentIndex + 1)
                        if (endIndex != -1) {
                            withStyle(SpanStyle(color = Color(0xFF2E7D32))) { // Green
                                append(inputText.text.substring(currentIndex, endIndex + 1))
                            }
                            currentIndex = endIndex + 1
                            continue
                        }
                    }

                    // Handle keywords
                    char.isLetter() -> {
                        val word = inputText.text.substring(currentIndex).takeWhile { it.isLetter() }
                        if (word in listOf("true", "false", "null")) {
                            withStyle(SpanStyle(color = Color(0xFF9C27B0))) { // Purple
                                append(word)
                            }
                            currentIndex += word.length
                            continue
                        }
                    }

                    // Handle numbers
                    char.isDigit() || (char == '-' && currentIndex + 1 < inputText.text.length && inputText.text[currentIndex + 1].isDigit()) -> {
                        val numStartIndex = currentIndex
                        var numEndIndex = currentIndex

                        // Handle negative numbers
                        if (char == '-') {
                            numEndIndex++
                        }

                        // Parse digits
                        while (numEndIndex < inputText.text.length && inputText.text[numEndIndex].isDigit()) {
                            numEndIndex++
                        }

                        // Handle decimal point
                        if (numEndIndex < inputText.text.length && inputText.text[numEndIndex] == '.') {
                            numEndIndex++
                            while (numEndIndex < inputText.text.length && inputText.text[numEndIndex].isDigit()) {
                                numEndIndex++
                            }
                        }

                        // Handle exponent
                        if (numEndIndex < inputText.text.length && (inputText.text[numEndIndex] == 'e' || inputText.text[numEndIndex] == 'E')) {
                            numEndIndex++
                            if (numEndIndex < inputText.text.length && (inputText.text[numEndIndex] == '+' || inputText.text[numEndIndex] == '-')) {
                                numEndIndex++
                            }
                            while (numEndIndex < inputText.text.length && inputText.text[numEndIndex].isDigit()) {
                                numEndIndex++
                            }
                        }

                        withStyle(SpanStyle(color = Color(0xFF2196F3))) { // Blue
                            append(inputText.text.substring(numStartIndex, numEndIndex))
                        }
                        currentIndex = numEndIndex
                        continue
                    }

                    // Handle brackets and colons
                    char in "{}[]:" -> {
                        withStyle(SpanStyle(color = Color(0xFFFF9800))) { // Orange
                            append(char)
                        }
                        currentIndex++
                        continue
                    }

                    // Handle property keys (words before colons)
                    char.isLetterOrDigit() || char == '_' -> {
                        val keyStartIndex = currentIndex
                        var keyEndIndex = currentIndex

                        while (keyEndIndex < inputText.text.length &&
                            (inputText.text[keyEndIndex].isLetterOrDigit() || inputText.text[keyEndIndex] == '_')
                        ) {
                            keyEndIndex++
                        }

                        // Check if this is a property key (followed by a colon after optional whitespace)
                        var tempIndex = keyEndIndex
                        while (tempIndex < inputText.text.length && inputText.text[tempIndex].isWhitespace()) {
                            tempIndex++
                        }

                        if (tempIndex < inputText.text.length && inputText.text[tempIndex] == ':') {
                            withStyle(SpanStyle(color = Color(0xFFFFEB3B))) { // Yellow
                                append(inputText.text.substring(keyStartIndex, keyEndIndex))
                            }
                            currentIndex = keyEndIndex
                            continue
                        }
                    }
                }

                // Default text
                append(char)
                currentIndex++
            }
        }

        // Use IdentityOffsetMapping to preserve cursor position relative to the text
        TransformedText(annotatedString, OffsetMapping.Identity)
    }

    Column {
        // Show error if any
        jsonError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // JSON editor with syntax highlighting
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            visualTransformation = jsonSyntaxHighlighting,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            isError = jsonError != null,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = MaterialTheme.colors.surface
            )
        )
    }
}

// Helper function to find the closing quote in a JSON string, accounting for escape sequences
private fun findClosingQuote(text: String, startIndex: Int): Int {
    var i = startIndex
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i += 2 // Skip the escaped character
            '"' -> return i // Found the closing quote
            else -> i++
        }
    }
    return -1 // No closing quote found
}

@Composable
fun propertyEditor(
    property: TerraformProperty,
    block: Block,
    blockKey: String,
    onPropertyChange: (String, String) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${property.name}${if (property.required) " *" else ""}",
                    style = MaterialTheme.typography.subtitle2,
                    // Grey out deprecated properties
                    color = if (property.deprecated) Color.Gray else LocalContentColor.current
                )

                if (property.required) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(required)",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary
                    )
                }

                // Add deprecated indicator
                if (property.deprecated) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(deprecated)",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }
            }

            // Show remove button for optional properties
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove property",
                        tint = MaterialTheme.colors.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Property editor based on type
        when (property.type) {
            PropertyType.STRING -> {
                // Use a key combining block ID and property name to reset state
                key(blockKey, property.name) {
                    var value by remember { mutableStateOf(block.getProperty(property.name) ?: property.default ?: "") }

                    // Update value when currentValue changes (e.g., from outside)
                    LaunchedEffect(block.getProperty(property.name)) {
                        value = block.getProperty(property.name) ?: property.default ?: ""
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
                    var value by remember { mutableStateOf(block.getProperty(property.name) ?: property.default ?: "") }

                    // Update value when currentValue changes (e.g., from outside)
                    LaunchedEffect(block.getProperty(property.name)) {
                        value = block.getProperty(property.name) ?: property.default ?: ""
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
                    var checked by remember {
                        mutableStateOf(
                            block.getProperty(property.name)?.lowercase(Locale.getDefault()) == "true"
                        )
                    }

                    // Update checked state when currentValue changes (e.g., from outside)
                    LaunchedEffect(block.getProperty(property.name)) {
                        checked = block.getProperty(property.name)?.lowercase(Locale.getDefault()) == "true"
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
                    var selectedOption by remember {
                        mutableStateOf(
                            block.getProperty(property.name) ?: property.default ?: ""
                        )
                    }

                    // Update selected option when currentValue changes (e.g., from outside)
                    LaunchedEffect(block.getProperty(property.name)) {
                        selectedOption = block.getProperty(property.name) ?: property.default ?: ""
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

            PropertyType.ARRAY -> {
                key(blockKey, property.name) {
                    arrayPropertyEditor(
                        currentValue = block.getProperty(property.name) ?: "",
                        onValueChange = { onPropertyChange(property.name, it) }
                    )
                }
            }

            PropertyType.MAP -> {
                Text("MAP property editor not implemented yet")
            }

            PropertyType.SET -> {
                Text("SET property editor not implemented yet")
            }

            PropertyType.JSON -> {
                key(blockKey, property.name) {
                    jsonPropertyEditor(
                        currentValue = block.getProperty(property.name) ?: "",
                        onValueChange = { onPropertyChange(property.name, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun arrayPropertyEditor(
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var items by remember {
        mutableStateOf(
            if (currentValue.isBlank()) emptyList()
            else currentValue.trim('[', ']').split(",")
                .map { it.trim().trim('"') }
                .filter { it.isNotEmpty() }
        )
    }

    Column {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = item,
                    onValueChange = { newValue ->
                        items = items.toMutableList().apply {
                            set(index, newValue)
                        }
                        onValueChange(items.joinToString(",", "[", "]") { "\"$it\"" })
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        items = items.toMutableList().apply {
                            removeAt(index)
                        }
                        onValueChange(items.joinToString(",", "[", "]") { "\"$it\"" })
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove item",
                        tint = MaterialTheme.colors.error
                    )
                }
            }

            if (index < items.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        TextButton(
            onClick = {
                items = items + ""
                onValueChange(items.joinToString(",", "[", "]") { "\"$it\"" })
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Add Item")
        }
    }
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
    val blockKey = remember(block.id) { block.id }

    // Get all available properties
    val allProperties = TerraformProperties.getPropertiesForBlock(block)

    // Organize properties into categories
    val (requiredProps, optionalProps) = allProperties.partition { it.required }

    // Further split optional properties into those with values and those without
    val optionalWithValues = optionalProps.filter { prop ->
        block.getProperty(prop.name)?.isNotEmpty() == true
    }.sortedBy { it.name }

    val optionalWithoutValues = optionalProps.filter { prop ->
        block.getProperty(prop.name)?.isNotEmpty() != true
    }.sortedBy { it.name }

    // Track which optional properties are being shown
    var shownOptionalProps by remember { mutableStateOf(optionalWithValues.map { it.name }.toSet()) }

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
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = when (block.type) {
                                BlockType.COMPUTE -> Color(0xFF4C97FF)
                                BlockType.DATABASE -> Color(0xFFFFAB19)
                                BlockType.NETWORKING -> Color(0xFF8C1A)
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

            if (allProperties.isEmpty()) {
                Text(
                    text = "No properties available for $blockContent.",
                    style = MaterialTheme.typography.body2
                )
            } else {
                // Required properties first (sorted alphabetically)
                requiredProps.sortedBy { it.name }.forEach { property ->
                    propertyEditor(
                        property = property,
                        block = block,
                        blockKey = blockKey,
                        onPropertyChange = onPropertyChange
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Optional properties that have values
                optionalWithValues.forEach { property ->
                    propertyEditor(
                        property = property,
                        block = block,
                        blockKey = blockKey,
                        onPropertyChange = onPropertyChange,
                        onRemove = {
                            shownOptionalProps = shownOptionalProps - property.name
                            onPropertyChange(property.name, "") // Clear the value
                        }
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Show additional optional properties that have been added
                optionalWithoutValues
                    .filter { prop -> prop.name in shownOptionalProps }
                    .forEach { property ->
                        propertyEditor(
                            property = property,
                            block = block,
                            blockKey = blockKey,
                            onPropertyChange = onPropertyChange,
                            onRemove = {
                                shownOptionalProps = shownOptionalProps - property.name
                                onPropertyChange(property.name, "") // Clear the value
                            }
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                // Add Property button (only show if there are unshown optional properties)
                if (optionalWithoutValues.any { it.name !in shownOptionalProps }) {
                    var showDropdown by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { showDropdown = true },
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Add property"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Property")
                        }

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            // Split properties into non-deprecated and deprecated
                            val (nonDeprecated, deprecated) = optionalWithoutValues
                                .filter { it.name !in shownOptionalProps }
                                .partition { !it.deprecated }

                            // Show non-deprecated properties first
                            nonDeprecated.forEach { property ->
                                DropdownMenuItem(
                                    onClick = {
                                        shownOptionalProps = shownOptionalProps + property.name
                                        showDropdown = false
                                    }
                                ) {
                                    Text(property.name)
                                }
                            }

                            // Add a divider if we have both types
                            if (nonDeprecated.isNotEmpty() && deprecated.isNotEmpty()) {
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                            }

                            // Show deprecated properties
                            deprecated.forEach { property ->
                                DropdownMenuItem(
                                    onClick = {
                                        shownOptionalProps = shownOptionalProps + property.name
                                        showDropdown = false
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = property.name,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "(deprecated)",
                                            style = MaterialTheme.typography.caption,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}