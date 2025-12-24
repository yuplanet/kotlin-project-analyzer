package org.example.data.symbol.expression

data class FieldFromMethodExpression (
    var target: FieldInfo? = null,
    val receiver: VariableInfo? = null,
    val method: MethodInfo = MethodInfo(),
): BaseExpression()