package terraformbuilder.terraform

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class VariableStateTest {

    @Test
    fun `test adding variables`() {
        val state = VariableState()
        
        // Create test variables
        val var1 = TerraformVariable(
            name = "api_domain",
            type = VariableType.STRING,
            description = "API domain name",
            defaultValue = null,
            sensitive = false
        )
        
        val var2 = TerraformVariable(
            name = "api_key",
            type = VariableType.STRING,
            description = "API Key",
            defaultValue = null,
            sensitive = true
        )
        
        // Add variables to state
        state.addVariable(var1)
        state.addVariable(var2)
        
        // Check variables were added
        assertEquals(2, state.variables.size)
        assertTrue(state.variables.any { it.name == "api_domain" })
        assertTrue(state.variables.any { it.name == "api_key" && it.sensitive })
    }
    
    @Test
    fun `test removing variables`() {
        val state = VariableState()
        
        // Create and add test variables
        val var1 = TerraformVariable(
            name = "api_domain",
            type = VariableType.STRING,
            description = "API domain name",
            defaultValue = null
        )
        
        val var2 = TerraformVariable(
            name = "api_key",
            type = VariableType.STRING,
            description = "API Key",
            defaultValue = null,
            sensitive = true
        )
        
        state.addVariable(var1)
        state.addVariable(var2)
        assertEquals(2, state.variables.size)
        
        // Remove variable
        state.removeVariable("api_key")
        
        // Check variables
        assertEquals(1, state.variables.size)
        assertTrue(state.variables.any { it.name == "api_domain" })
        assertFalse(state.variables.any { it.name == "api_key" })
    }
    
    @Test
    fun `test updating variables`() {
        val state = VariableState()
        
        // Create and add a test variable
        val initialVar = TerraformVariable(
            name = "api_domain",
            type = VariableType.STRING,
            description = "API domain name",
            defaultValue = "api.example.com"
        )
        
        state.addVariable(initialVar)
        
        // Update the variable
        val updatedVar = TerraformVariable(
            name = "api_domain",
            type = VariableType.STRING,
            description = "Updated API domain name",
            defaultValue = "api.updated.com",
            sensitive = true
        )
        
        state.updateVariable("api_domain", updatedVar)
        
        // Check the variable was updated
        assertEquals(1, state.variables.size)
        val variable = state.variables.first()
        
        assertEquals("api_domain", variable.name)
        assertEquals(VariableType.STRING, variable.type)
        assertEquals("Updated API domain name", variable.description)
        assertEquals("api.updated.com", variable.defaultValue)
        assertTrue(variable.sensitive)
    }
    
    @Test
    fun `test clearing all variables`() {
        val state = VariableState()
        
        // Add several variables
        state.addVariable(TerraformVariable("var1", VariableType.STRING, "Var 1"))
        state.addVariable(TerraformVariable("var2", VariableType.NUMBER, "Var 2"))
        state.addVariable(TerraformVariable("var3", VariableType.BOOL, "Var 3"))
        
        assertEquals(3, state.variables.size)
        
        // Clear all variables
        state.clearAll()
        
        // Verify all variables were removed
        assertEquals(0, state.variables.size)
    }
} 