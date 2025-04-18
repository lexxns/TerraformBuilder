package terraformbuilder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
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
    val primaryColor = MaterialTheme.colors.primary

    // Use TextFieldValue to track both text content and selection position
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = currentValue))
    }

    // Update text when currentValue changes from outside
    LaunchedEffect(currentValue) {
        if (currentValue != text) {
            text = currentValue
            // Preserve selection if possible
            textFieldValue = textFieldValue.copy(text = currentValue)
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

    // Handle text changes with indentation
    fun handleTextChange(newValue: TextFieldValue) {
        val newText = newValue.text
        val cursorPosition = newValue.selection.start

        // Check if a newline was just added
        val oldText = textFieldValue.text

        // Detect if a newline was just typed at the cursor position
        if (newText.length > oldText.length &&
            cursorPosition > 0 &&
            newText[cursorPosition - 1] == '\n'
        ) {

            // Find the line that was just created
            val lines = newText.split('\n')
            val currentLineIndex = newText.substring(0, cursorPosition).count { it == '\n' }

            // If we're not at the beginning of the file
            if (currentLineIndex > 0) {
                // Find the indentation of the previous line
                val previousLine = lines[currentLineIndex - 1]
                val previousIndent = previousLine.takeWhile { it.isWhitespace() }

                // Analyze previous line content
                val trimmedPrevLine = previousLine.trim()

                // Determine if we need to adjust indentation
                val indentation = when {
                    // Increase indent after opening brace
                    trimmedPrevLine.endsWith('{') || trimmedPrevLine.endsWith('[') ->
                        "$previousIndent    "

                    // Decrease indent if previous line started with closing brace
                    trimmedPrevLine.startsWith('}') || trimmedPrevLine.startsWith(']') ->
                        if (previousIndent.length >= 4) previousIndent.substring(0, previousIndent.length - 4)
                        else ""

                    // Keep same indentation otherwise
                    else -> previousIndent
                }

                // Special case: if the current line starts with a closing brace,
                // reduce indentation by one level
                val currentLine = if (currentLineIndex < lines.size) lines[currentLineIndex] else ""
                val finalIndentation = if (currentLine.trim().startsWith('}') || currentLine.trim().startsWith(']')) {
                    if (indentation.length >= 4) indentation.substring(0, indentation.length - 4)
                    else ""
                } else {
                    indentation
                }

                // Insert indentation at the cursor position
                val beforeCursor = newText.substring(0, cursorPosition)
                val afterCursor = newText.substring(cursorPosition)
                val finalText = beforeCursor + finalIndentation + afterCursor

                // Update text and cursor position
                text = finalText
                textFieldValue = TextFieldValue(
                    text = finalText,
                    selection = TextRange(cursorPosition + finalIndentation.length)
                )
                return
            }
        }

        // Normal case - update text and TextFieldValue
        text = newText
        textFieldValue = newValue
    }

    // Create a visual transformation for JSON syntax highlighting
    val jsonSyntaxHighlighting = VisualTransformation { inputText ->
        val annotatedString = buildAnnotatedString {
            var currentIndex = 0

            while (currentIndex < inputText.text.length) {
                val char = inputText.text[currentIndex]

                when {
                    // Handle keywords first (true, false, null)
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

                    // Handle numbers (including negative, decimal, and scientific notation)
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
                            withStyle(SpanStyle(color = primaryColor)) {
                                append(inputText.text.substring(keyStartIndex, keyEndIndex))
                            }
                            currentIndex = keyEndIndex
                            continue
                        }
                    }

                    // Handle strings
                    char == '"' -> {
                        val endIndex = findClosingQuote(inputText.text, currentIndex + 1)
                        if (endIndex != -1) {
                            // Check if this is a JSON key (followed by a colon after optional whitespace)
                            var tempIndex = endIndex + 1
                            while (tempIndex < inputText.text.length && inputText.text[tempIndex].isWhitespace()) {
                                tempIndex++
                            }

                            if (tempIndex < inputText.text.length && inputText.text[tempIndex] == ':') {
                                // This is a JSON key, color it with primary color
                                withStyle(SpanStyle(color = primaryColor)) {
                                    append(inputText.text.substring(currentIndex, endIndex + 1))
                                }
                            } else {
                                // This is a regular string value, color it green
                                withStyle(SpanStyle(color = Color(0xFF2E7D32))) {
                                    append(inputText.text.substring(currentIndex, endIndex + 1))
                                }
                            }
                            currentIndex = endIndex + 1
                            continue
                        }
                    }

                    // Handle brackets and colons
                    char in "{}[]:" -> {
                        withStyle(SpanStyle(color = Color(0xFFFF9800))) { // Orange
                            append(char)
                        }
                        currentIndex++
                        continue
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
            value = textFieldValue,
            onValueChange = { newValue ->
                handleTextChange(newValue)
            },
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
    onRemove: (() -> Unit)? = null,
    onNavigateToVariable: ((String) -> Unit)? = null,
    onNavigateToResource: ((String, String) -> Unit)? = null
) {
    println("DEBUG: Rendering property editor for '${property.name}'")
    println("DEBUG: Property description: '${property.description}'")

    var showTooltip by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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

                // Add help icon with tooltip for property description
                if (property.description.isNotEmpty()) {
                    println("DEBUG: Showing help icon for '${property.name}'")
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        IconButton(
                            onClick = { showTooltip = !showTooltip },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Help,
                                contentDescription = "Show property description",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colors.primary
                            )
                        }
                        if (showTooltip) {
                            Popup(
                                onDismissRequest = { showTooltip = false }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .widthIn(max = 300.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    elevation = 4.dp,
                                    color = MaterialTheme.colors.surface
                                ) {
                                    Text(
                                        text = property.description,
                                        style = MaterialTheme.typography.body2,
                                        modifier = Modifier.padding(8.dp),
                                        maxLines = 5
                                    )
                                }
                            }
                        }
                    }
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
                    val rawValue = block.getProperty(property.name) ?: property.default ?: ""
                    var editMode by remember { mutableStateOf(false) }

                    // For text editing mode
                    var editValue by remember { mutableStateOf(rawValue) }

                    // Update when property changes externally
                    LaunchedEffect(block.getProperty(property.name)) {
                        editValue = block.getProperty(property.name) ?: property.default ?: ""
                    }

                    // Improved regex patterns for terraform expressions
                    // Separate pattern for interpolation vs standalone references
                    val interpolationPattern = remember { Regex("\\$\\{([^}]+)}") }
                    val varPattern = remember { Regex("var\\.[a-zA-Z0-9_]+") }
                    val resourcePattern = remember { Regex("[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+") }

                    // Parse string into segments, properly handling interpolation
                    val segments = remember(rawValue) {
                        val result =
                            mutableListOf<Triple<String, Boolean, String>>() // (text, isReference, originalText)
                        var lastEnd = 0

                        // First, find all interpolations ${...}
                        interpolationPattern.findAll(rawValue).forEach { match ->
                            // Add text before the interpolation
                            if (match.range.first > lastEnd) {
                                result.add(Triple(rawValue.substring(lastEnd, match.range.first), false, ""))
                            }

                            // Extract the inner content
                            val innerContent = match.groupValues[1]
                            val originalText = match.value // The entire ${...} text

                            // Check if inner content is a variable reference
                            val varMatch = varPattern.find(innerContent)
                            if (varMatch != null) {
                                // It's a variable reference inside interpolation
                                result.add(Triple(varMatch.value, true, originalText))
                            } else {
                                // Check if it's a resource reference
                                val resourceMatch = resourcePattern.find(innerContent)
                                if (resourceMatch != null) {
                                    // It's a resource reference inside interpolation
                                    result.add(Triple(resourceMatch.value, true, originalText))
                                } else {
                                    // Not a recognized reference, treat as plain text
                                    result.add(Triple(originalText, false, ""))
                                }
                            }

                            lastEnd = match.range.last + 1
                        }

                        // Handle remaining text after last interpolation
                        if (lastEnd < rawValue.length) {
                            // Check for standalone references in remaining text
                            val remaining = rawValue.substring(lastEnd)
                            val standaloneVarMatch = varPattern.find(remaining)

                            if (standaloneVarMatch != null) {
                                // There's a standalone var reference
                                if (standaloneVarMatch.range.first > 0) {
                                    // Add text before the reference
                                    result.add(
                                        Triple(
                                            remaining.substring(0, standaloneVarMatch.range.first),
                                            false,
                                            ""
                                        )
                                    )
                                }

                                // Add the reference
                                result.add(Triple(standaloneVarMatch.value, true, standaloneVarMatch.value))

                                // Add text after the reference
                                if (standaloneVarMatch.range.last + 1 < remaining.length) {
                                    result.add(
                                        Triple(
                                            remaining.substring(standaloneVarMatch.range.last + 1),
                                            false,
                                            ""
                                        )
                                    )
                                }
                            } else {
                                // No references, add as plain text
                                result.add(Triple(remaining, false, ""))
                            }
                        }

                        // If there were no matches, just add the whole string
                        if (result.isEmpty()) {
                            result.add(Triple(rawValue, false, ""))
                        }

                        result
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Toggle button for edit mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { editMode = !editMode }) {
                                Text(if (editMode) "Block View" else "Text Edit")
                            }
                        }

                        if (editMode) {
                            // Text edit mode - simple text field
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = {
                                    editValue = it
                                    onPropertyChange(property.name, it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            // Block mode - Scratch-like interface with reference blocks
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Render each segment
                                segments.forEach { (text, isReference, originalText) ->
                                    if (isReference) {
                                        // Render as a reference block
                                        val isVariable = text.startsWith("var.")
                                        val backgroundColor = if (isVariable) {
                                            MaterialTheme.colors.primary
                                        } else {
                                            MaterialTheme.colors.secondary
                                        }

                                        // Check if this was part of interpolation
                                        val isInterpolated = originalText.startsWith("\${")

                                        // Create a row for the reference with optional interpolation syntax
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        ) {
                                            // Show interpolation start if needed
                                            if (isInterpolated) {
                                                Text(
                                                    text = "\${",
                                                    modifier = Modifier.padding(end = 2.dp)
                                                )
                                            }

                                            // The reference itself
                                            Button(
                                                onClick = {
                                                    // Navigate to the reference
                                                    if (isVariable && onNavigateToVariable != null) {
                                                        val varName = text.substring(4)
                                                        onNavigateToVariable(varName)
                                                    } else if (text.contains(".") && onNavigateToResource != null) {
                                                        val parts = text.split(".")
                                                        if (parts.size >= 2) {
                                                            onNavigateToResource(parts[0], parts[1])
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    backgroundColor = backgroundColor
                                                ),
                                                contentPadding = PaddingValues(
                                                    horizontal = 8.dp,
                                                    vertical = 2.dp
                                                ),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = text,
                                                    color = MaterialTheme.colors.onPrimary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            // Show interpolation end if needed
                                            if (isInterpolated) {
                                                Text(
                                                    text = "}",
                                                    modifier = Modifier.padding(start = 2.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        // Render as regular text
                                        Text(
                                            text = text,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
    onNavigateToVariable: ((String) -> Unit)? = null,
    onNavigateToResource: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = BlockTypeColors.getColor(block.type),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = blockContent,
                    style = MaterialTheme.typography.h6
                )
            }

            // Block description
            Text(
                text = block.description,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                        onPropertyChange = onPropertyChange,
                        onNavigateToVariable = onNavigateToVariable,
                        onNavigateToResource = onNavigateToResource
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
                        },
                        onNavigateToVariable = onNavigateToVariable,
                        onNavigateToResource = onNavigateToResource
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