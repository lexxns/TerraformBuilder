package terraformbuilder.components.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import terraformbuilder.components.EditorViewState
import terraformbuilder.components.block.BlockState
import terraformbuilder.components.block.CompositeBlock
import terraformbuilder.github.GithubService
import terraformbuilder.github.GithubUrlParser
import terraformbuilder.terraform.LocalDirectoryLoader
import terraformbuilder.terraform.TerraformGenerationHandler
import terraformbuilder.terraform.TerraformParser
import terraformbuilder.terraform.VariableState

@Composable
fun editorDialogs(
    viewState: EditorViewState,
    selectedTemplateFactory: ((String, Offset) -> CompositeBlock)?,
    blockState: BlockState,
    variableState: VariableState,
    isLoading: Boolean,
    errorMessage: String?,
    setIsLoading: (Boolean) -> Unit,
    setErrorMessage: (String?) -> Unit,
    coroutineScope: CoroutineScope,
    terraformGenerationHandler: TerraformGenerationHandler
) {
    // Template dialog
    templateDialog(
        isVisible = viewState.showTemplateDialog,
        templateName = viewState.templateName,
        selectedTemplateName = viewState.selectedTemplateName,
        onTemplateNameChange = { viewState.updateTemplateName(it) },
        onConfirm = { templateName ->
            selectedTemplateFactory?.let { factory ->
                // Calculate position at the center of the current view
                val viewportCenter = Offset(
                    (800f / 2f - viewState.panOffset.x) / viewState.scale,
                    (600f / 2f - viewState.panOffset.y) / viewState.scale
                )

                // Create and add the composite
                val composite = factory(templateName, viewportCenter)
                blockState.addCompositeBlock(composite)

                // Select the new composite
                viewState.selectComposite(composite.id)
            }
            viewState.hideDialog(EditorViewState.DialogType.TEMPLATE)
        },
        onDismiss = { viewState.hideDialog(EditorViewState.DialogType.TEMPLATE) }
    )

    // Group dialog
    groupDialog(
        isVisible = viewState.showGroupDialog,
        groupName = viewState.groupNameInput,
        onGroupNameChange = { viewState.updateGroupName(it) },
        onConfirm = {
            blockState.groupBlocks(viewState.selectedBlockIds, viewState.groupNameInput)
            viewState.clearMultiSelection()
            viewState.hideDialog(EditorViewState.DialogType.GROUP)
        },
        onDismiss = { viewState.hideDialog(EditorViewState.DialogType.GROUP) }
    )

    // GitHub dialog
    if (viewState.showGithubDialog) {
        githubUrlDialog(
            onDismiss = {
                viewState.hideDialog(EditorViewState.DialogType.GITHUB)
                setErrorMessage(null)
            },
            onConfirm = { url ->
                coroutineScope.launch {
                    setIsLoading(true)
                    setErrorMessage(null)
                    try {
                        println("APP: Processing GitHub URL: $url")
                        val repoInfo = GithubUrlParser.parse(url)
                        if (repoInfo != null) {
                            println("APP: Parsed repo info: $repoInfo")

                            // Clear existing blocks and variables
                            blockState.clearAll()
                            variableState.clearAll()

                            val githubService = GithubService()
                            val files = githubService.loadTerraformFiles(repoInfo)

                            if (files.isEmpty()) {
                                setErrorMessage("No Terraform files found in repository")
                                return@launch
                            }

                            // Process the loaded files
                            processLoadedTerraformFiles(files, blockState, variableState)

                            viewState.hideDialog(EditorViewState.DialogType.GITHUB)
                        } else {
                            setErrorMessage("Invalid GitHub URL format")
                        }
                    } catch (e: Exception) {
                        println("APP: Error loading Terraform: ${e.message}")
                        e.printStackTrace()
                        setErrorMessage("Error: ${e.message}")
                    } finally {
                        setIsLoading(false)
                    }
                }
            },
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }

    // Local directory dialog
    if (viewState.showLocalDirectoryDialog) {
        loadDirectoryDialog(
            onDismiss = {
                viewState.hideDialog(EditorViewState.DialogType.LOCAL_DIRECTORY)
                setErrorMessage(null)
            },
            onSelectDirectory = { directory ->
                coroutineScope.launch {
                    setIsLoading(true)
                    setErrorMessage(null)
                    try {
                        println("APP: Processing local directory: ${directory.absolutePath}")

                        // Clear existing blocks and variables
                        blockState.clearAll()
                        variableState.clearAll()

                        val directoryLoader = LocalDirectoryLoader()
                        val files = directoryLoader.loadTerraformFiles(directory)

                        if (files.isEmpty()) {
                            setErrorMessage("No Terraform files found in directory")
                            return@launch
                        }

                        // Process the loaded files
                        processLoadedTerraformFiles(files, blockState, variableState)

                        viewState.hideDialog(EditorViewState.DialogType.LOCAL_DIRECTORY)
                    } catch (e: Exception) {
                        println("APP: Error loading Terraform: ${e.message}")
                        e.printStackTrace()
                        setErrorMessage("Error: ${e.message}")
                    } finally {
                        setIsLoading(false)
                    }
                }
            },
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }

    // Variables dialog
    if (viewState.showVariablesDialog) {
        variableDialog(
            onDismiss = { viewState.hideDialog(EditorViewState.DialogType.VARIABLES) },
            variables = variableState.variables,
            onAddVariable = { variableState.addVariable(it) },
            onRemoveVariable = { variableState.removeVariable(it) },
            onUpdateVariable = { name, variable ->
                variableState.updateVariable(name, variable)
            }
        )
    }

    // Terraform generation dialog
    if (terraformGenerationHandler.showDialog.value) {
        showTerraformGenerationHandler(terraformGenerationHandler)
    }

    // File dialog component
    terraformGenerationHandler.fileDialogComponent(blockState, variableState, coroutineScope)
}

@Composable
private fun showTerraformGenerationHandler(terraformGenerationHandler: TerraformGenerationHandler) {
    AlertDialog(
        onDismissRequest = { terraformGenerationHandler.closeDialog() },
        title = { Text("Generate Terraform Code") },
        text = {
            if (terraformGenerationHandler.isGenerating.value) {
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
                    terraformGenerationHandler.resultMessage.value
                        ?: "Choose a directory where Terraform files will be generated."
                )
            }
        },
        confirmButton = {
            if (!terraformGenerationHandler.isGenerating.value && terraformGenerationHandler.resultMessage.value == null) {
                Button(onClick = { terraformGenerationHandler.showFileSelector() }) {
                    Text("Select Directory")
                }
            } else {
                TextButton(onClick = { terraformGenerationHandler.closeDialog() }) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (!terraformGenerationHandler.isGenerating.value && terraformGenerationHandler.resultMessage.value == null) {
                TextButton(onClick = { terraformGenerationHandler.closeDialog() }) {
                    Text("Cancel")
                }
            }
        }
    )
}

// Helper function for processing Terraform files
private fun processLoadedTerraformFiles(
    files: List<String>,
    blockState: BlockState,
    variableState: VariableState
) {
    println("APP: Loaded ${files.size} files")

    val parser = TerraformParser()

    // Parse all files
    files.forEach { file ->
        val parseResult = parser.parse(file)

        // Add variables
        parseResult.variables.forEach { variable ->
            println("APP: Adding variable ${variable.name}")
            variableState.addVariable(variable)
        }

        // Convert and add resources
        val blocks = parser.convertToBlocks(parseResult.resources)
        blocks.forEachIndexed { index, block ->
            val row = index / 3
            val col = index % 3
            val position = Offset(
                50f + col * 100f,
                50f + row * 50f
            )
            println("APP: Adding block ${block.content} at position $position")
            blockState.addBlock(block.copy(_position = position))
        }
    }

    // Check if anything was loaded
    if (blockState.allBlocks.isEmpty() && variableState.variables.isEmpty()) {
        throw Exception("No Terraform resources or variables found in files")
    }
}