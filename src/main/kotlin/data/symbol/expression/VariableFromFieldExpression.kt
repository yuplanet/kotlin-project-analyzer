package org.example.data.symbol.expression

data class VariableFromFieldExpression(
    val target: VariableInfo? = null,
    val source: FieldInfo
): BaseExpression()