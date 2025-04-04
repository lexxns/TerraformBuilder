package terraformbuilder.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import terraformbuilder.terraform.VariableState
import terraformbuilder.terraformbuilder.TerraformCodeGenerator
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * A button component that triggers Terraform code generation.
 * Uses AWT FileDialog instead of JFileChooser to avoid threading issues.
 */
@Composable
fun generateTerraformButton(
    blockState: BlockState,
    variableState: VariableState,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var showFileDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = { showDialog = true },
        modifier = modifier.padding(16.dp)
    ) {
        Text("Generate Terraform")
    }

    // File picker dialog using AWT
    if (showFileDialog) {
        AwtWindow(
            create = {
                FileDialog(Frame(), "Select Output Directory", FileDialog.SAVE).apply {
                    isMultipleMode = false
                    file = "terraform_project" // Suggested name
                    isVisible = true
                }
            },
            dispose = { it.dispose() },
            update = { fileDialog ->
                // This block runs after the dialog is closed
                showFileDialog = false

                if (fileDialog.directory != null && fileDialog.file != null) {
                    val outputDir = File(fileDialog.directory, fileDialog.file)

                    // Start the generation process - in Compose Desktop we use Dispatchers.Default for UI
                    coroutineScope.launch {
                        isGenerating = true

                        try {
                            if (blockState.blocks.isEmpty()) {
                                resultMessage = "Error: No resources to generate. Please add at least one resource."
                                return@launch
                            }

                            // Run the actual generation in an IO context
                            withContext(Dispatchers.IO) {
                                // Ensure directory exists
                                outputDir.mkdirs()

                                // Generate code in the background thread
                                val generator = TerraformCodeGenerator()
                                generator.generateCode(
                                    blocks = blockState.blocks,
                                    connections = blockState.connections,
                                    variables = variableState.variables,
                                    outputDir = outputDir
                                )
                            }

                            // UI updates can be done directly in the coroutine scope in Compose Desktop
                            resultMessage = "Successfully generated Terraform code to: ${outputDir.absolutePath}"
                        } catch (e: Exception) {
                            e.printStackTrace()
                            resultMessage = "Error generating Terraform code: ${e.message}"
                        } finally {
                            isGenerating = false
                        }
                    }
                }
            }
        )
    }

    // Main dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isGenerating) {
                    showDialog = false
                    resultMessage = null
                }
            },
            title = { Text("Generate Terraform Code") },
            text = {
                if (isGenerating) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Generating Terraform code...")
                    }
                } else {
                    Text(
                        resultMessage ?: "Choose a directory where Terraform files will be generated."
                    )
                }
            },
            confirmButton = {
                if (!isGenerating && resultMessage == null) {
                    Button(
                        onClick = {
                            // Show file dialog
                            showFileDialog = true
                        }
                    ) {
                        Text("Select Directory")
                    }
                } else {
                    TextButton(
                        onClick = {
                            showDialog = false
                            resultMessage = null
                        }
                    ) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (!isGenerating && resultMessage == null) {
                    TextButton(
                        onClick = {
                            showDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}