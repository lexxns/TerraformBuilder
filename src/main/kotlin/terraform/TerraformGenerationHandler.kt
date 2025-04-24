package terraformbuilder.terraform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.AwtWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import terraformbuilder.components.block.BlockState
import terraformbuilder.terraformbuilder.TerraformCodeGenerator
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class TerraformGenerationHandler {
    // States that can be observed by UI components
    val isGenerating = mutableStateOf(false)
    val resultMessage = mutableStateOf<String?>(null)
    private val showFileDialog = mutableStateOf(false)
    val showDialog = mutableStateOf(false)

    /**
     * Initiates the Terraform generation process
     */
    fun startGeneration() {
        showDialog.value = true
    }

    /**
     * Shows the file dialog to select output directory
     */
    fun showFileSelector() {
        showFileDialog.value = true
    }

    /**
     * Creates the AWT file dialog component
     */
    @Composable
    fun fileDialogComponent(
        blockState: BlockState,
        variableState: VariableState,
        coroutineScope: CoroutineScope
    ) {
        if (showFileDialog.value) {
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
                    showFileDialog.value = false

                    if (fileDialog.directory != null && fileDialog.file != null) {
                        val outputDir = File(fileDialog.directory, fileDialog.file)
                        generateTerraform(blockState, variableState, outputDir, coroutineScope)
                    }
                }
            )
        }
    }

    /**
     * Handles the actual generation of Terraform code
     */
    private fun generateTerraform(
        blockState: BlockState,
        variableState: VariableState,
        outputDir: File,
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch {
            isGenerating.value = true

            try {
                if (blockState.allBlocks.isEmpty()) {
                    resultMessage.value = "Error: No resources to generate. Please add at least one resource."
                    return@launch
                }

                // Run the actual generation in an IO context
                withContext(Dispatchers.IO) {
                    // Ensure directory exists
                    outputDir.mkdirs()

                    // Generate code in the background thread
                    val generator = TerraformCodeGenerator()
                    generator.generateCode(
                        blocks = blockState.allBlocks,
                        connections = blockState.connections,
                        variables = variableState.variables,
                        outputDir = outputDir
                    )
                }

                resultMessage.value = "Successfully generated Terraform code to: ${outputDir.absolutePath}"
            } catch (e: Exception) {
                e.printStackTrace()
                resultMessage.value = "Error generating Terraform code: ${e.message}"
            } finally {
                isGenerating.value = false
            }
        }
    }

    /**
     * Closes dialogs and resets state
     */
    fun closeDialog() {
        if (!isGenerating.value) {
            showDialog.value = false
            resultMessage.value = null
        }
    }
}