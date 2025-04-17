package terraformbuilder.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

@Composable
fun loadDirectoryDialog(
    onDismiss: () -> Unit,
    onSelectDirectory: (File) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var selectedDirectoryPath by remember { mutableStateOf<String?>(null) }
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
                    "Load Terraform from Local Directory",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDirectoryPath ?: "No directory selected",
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            // Launch native file chooser dialog
                            val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)
                            fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            fileChooser.dialogTitle = "Select Terraform Directory"

                            val result = fileChooser.showOpenDialog(null)
                            if (result == JFileChooser.APPROVE_OPTION) {
                                val selectedDir = fileChooser.selectedFile
                                selectedDirectoryPath = selectedDir.absolutePath
                                validationError = null
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text("Browse...")
                    }
                }

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
                            if (selectedDirectoryPath != null) {
                                val dir = File(selectedDirectoryPath!!)
                                if (dir.isDirectory) {
                                    // Count Terraform files in directory
                                    val terraformFiles = dir.listFiles { file ->
                                        file.isFile && file.name.endsWith(".tf")
                                    }

                                    if (terraformFiles != null && terraformFiles.isNotEmpty()) {
                                        onSelectDirectory(dir)
                                    } else {
                                        validationError = "No Terraform (.tf) files found in the selected directory"
                                    }
                                } else {
                                    validationError = "Selected path is not a directory"
                                }
                            } else {
                                validationError = "Please select a directory"
                            }
                        },
                        enabled = !isLoading && selectedDirectoryPath != null
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