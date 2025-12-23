package org.example.data.symbol.expression

import org.example.data.symbol.FieldInfo
import org.example.data.symbol.VariableInfo

data class VariableToFieldAssignmentExpression(
    val target: VariableInfo? = null,
    val source: FieldInfo? = null
): BaseExpression()