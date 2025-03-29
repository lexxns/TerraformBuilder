package terraformbuilder.terraform

import androidx.compose.runtime.mutableStateListOf

data class TerraformVariable(
    val name: String,
    val type: VariableType,
    val description: String = "",
    val defaultValue: String? = null,
    val sensitive: Boolean = false
)

enum class VariableType {
    STRING, NUMBER, BOOL, LIST, MAP;

    fun displayName(): String = when (this) {
        STRING -> "String"
        NUMBER -> "Number"
        BOOL -> "Boolean"
        LIST -> "List"
        MAP -> "Map"
    }
}

class VariableState {
    private val _variables = mutableStateListOf<TerraformVariable>()
    val variables: List<TerraformVariable> = _variables

    fun addVariable(variable: TerraformVariable) {
        if (!_variables.any { it.name == variable.name }) {
            _variables.add(variable)
        }
    }

    fun removeVariable(name: String) {
        _variables.removeAll { it.name == name }
    }

    fun updateVariable(name: String, updatedVariable: TerraformVariable) {
        val index = _variables.indexOfFirst { it.name == name }
        if (index != -1) {
            _variables[index] = updatedVariable
        }
    }

    fun getVariable(name: String): TerraformVariable? {
        return _variables.find { it.name == name }
    }
}