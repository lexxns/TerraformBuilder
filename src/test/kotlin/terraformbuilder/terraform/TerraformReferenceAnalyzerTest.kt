package terraformbuilder.terraform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerraformReferenceAnalyzerTest {

    @Test
    fun `test parseExpression with interpolation markers`() {
        val analyzer = TerraformReferenceAnalyzer()

        // Test string with our custom interpolation markers
        val testString =
            "Prefix-${TerraformConstants.INTERP_START}var.name_prefix${TerraformConstants.INTERP_END}-${TerraformConstants.INTERP_START}var.bucket_name${TerraformConstants.INTERP_END}"

        // Parse the string
        val segments = analyzer.parseExpression(testString)

        // Verify we get the expected segments
        assertEquals(4, segments.size, "Should parse into 4 segments")

        // First segment should be text
        assertTrue(segments[0] is TerraformReferenceAnalyzer.ExpressionSegment.Text)
        assertEquals("Prefix-", (segments[0] as TerraformReferenceAnalyzer.ExpressionSegment.Text).content)

        // Second segment should be variable reference
        assertTrue(segments[1] is TerraformReferenceAnalyzer.ExpressionSegment.VariableReference)
        val varRef1 = segments[1] as TerraformReferenceAnalyzer.ExpressionSegment.VariableReference
        assertEquals("name_prefix", varRef1.variableName)

        // Third segment should be text
        assertTrue(segments[2] is TerraformReferenceAnalyzer.ExpressionSegment.Text)
        assertEquals("-", (segments[2] as TerraformReferenceAnalyzer.ExpressionSegment.Text).content)

        // Fourth segment should be variable reference
        assertTrue(segments[3] is TerraformReferenceAnalyzer.ExpressionSegment.VariableReference)
        val varRef2 = segments[3] as TerraformReferenceAnalyzer.ExpressionSegment.VariableReference
        assertEquals("bucket_name", varRef2.variableName)
    }

    @Test
    fun `test resource reference parsing`() {
        val analyzer = TerraformReferenceAnalyzer()

        // Test string with resource reference
        val testString = "${TerraformConstants.INTERP_START}aws_s3_bucket.my_bucket.id${TerraformConstants.INTERP_END}"

        // Parse the string
        val segments = analyzer.parseExpression(testString)

        // Verify we get the expected segment
        assertEquals(1, segments.size, "Should parse into 1 segment")

        // Segment should be resource reference
        assertTrue(segments[0] is TerraformReferenceAnalyzer.ExpressionSegment.ResourceReference)
        val resRef = segments[0] as TerraformReferenceAnalyzer.ExpressionSegment.ResourceReference
        assertEquals("aws_s3_bucket", resRef.resourceType)
        assertEquals("my_bucket", resRef.resourceName)
        assertEquals("id", resRef.attribute)
    }

    @Test
    fun `test function call parsing`() {
        val analyzer = TerraformReferenceAnalyzer()

        // Test string with function call
        val testString =
            "${TerraformConstants.INTERP_START}format(\"%s-%s\", var.prefix, var.name)${TerraformConstants.INTERP_END}"

        // Parse the string
        val segments = analyzer.parseExpression(testString)

        // Verify we get the expected segment
        assertEquals(1, segments.size, "Should parse into 1 segment")

        // Segment should be function
        assertTrue(segments[0] is TerraformReferenceAnalyzer.ExpressionSegment.Function)
        val funcRef = segments[0] as TerraformReferenceAnalyzer.ExpressionSegment.Function
        assertEquals("format", funcRef.functionName)
        assertEquals("\"%s-%s\", var.prefix, var.name", funcRef.content)
    }
}