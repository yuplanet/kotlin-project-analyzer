package org.example.data.symbol.expression

data class VariableFromVariableExpression (
    var target: VariableInfo? = null,
    var source: VariableInfo? = null,
): BaseExpression()