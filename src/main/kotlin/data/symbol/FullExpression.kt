package org.example.data.symbol

data class FullExpression (

    var target: VariableInfo = VariableInfo(),

    val receiver: VariableInfo = VariableInfo(),

    val method: MethodInfo = MethodInfo(),
)