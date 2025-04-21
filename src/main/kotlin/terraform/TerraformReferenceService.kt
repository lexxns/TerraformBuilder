package terraformbuilder.terraform

import androidx.compose.ui.text.AnnotatedString
import terraformbuilder.components.Block
import terraformbuilder.components.formatResourceName

object TerraformReferenceService {
    private val analyzer = TerraformReferenceAnalyzer()

    fun findReferencesTo(variable: TerraformVariable, blocks: List<Block>): List<Block> {
        return blocks.filter { block ->
            block.properties.values.any { propertyValue ->
                analyzer.findVariableReferences(propertyValue)
                    .contains(variable.name)
            }
        }
    }

    fun findReferencesTo(block: Block, allBlocks: List<Block>): List<Block> {
        val resourceType = block.resourceType.resourceName
        val resourceName = formatResourceName(block.content)

        return allBlocks.filter { otherBlock ->
            otherBlock.id != block.id &&
                    otherBlock.properties.values.any { propertyValue ->
                        analyzer.findResourceReferences(propertyValue)
                            .any { (refType, refName) ->
                                refType == resourceType && refName == resourceName
                            }
                    }
        }
    }

    fun highlightInterpolations(text: String): AnnotatedString {
        return analyzer.highlightExpression(text)
    }
}