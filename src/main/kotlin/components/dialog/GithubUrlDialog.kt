package terraformbuilder.components.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun githubUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var url by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(400.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Load Terraform from GitHub",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        validationError = null
                    },
                    label = { Text("GitHub URL") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = validationError != null || errorMessage != null,
                    enabled = !isLoading
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (url.isNotEmpty() && url.contains("github.com")) {
                                onConfirm(url)
                            } else {
                                validationError = "Please enter a valid GitHub URL"
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colors.onPrimary
                            )
                        } else {
                            Text("Load")
                        }
                    }
                }
            }
        }
    }
} 