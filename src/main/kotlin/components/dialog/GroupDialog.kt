package terraformbuilder.components.dialog

import androidx.compose.material.*
import androidx.compose.runtime.Composable

@Composable
fun groupDialog(
    isVisible: Boolean,
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Create Group") },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text("Group Name") }
                )
            },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text("Create Group")
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