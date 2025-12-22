package org.example.data.symbol.expression

import org.example.data.symbol.MethodInfo
import org.example.data.symbol.VariableInfo

data class VariableAssignmentExpression (
    var target: VariableInfo? = null,
    val receiver: VariableInfo? = null,
    val method: MethodInfo = MethodInfo(),
): BaseExpression()