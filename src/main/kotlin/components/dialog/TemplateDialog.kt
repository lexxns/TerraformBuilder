package terraformbuilder.components.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun templateDialog(
    isVisible: Boolean,
    templateName: String,
    selectedTemplateName: String,
    onTemplateNameChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Create $selectedTemplateName") },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = onTemplateNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { onConfirm(templateName) }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}