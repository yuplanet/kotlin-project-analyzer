package org.example.data.symbol.expression

import org.example.data.symbol.FieldInfo
import org.example.data.symbol.VariableInfo


data  class FieldAssignmentExpression(
    var target: FieldInfo? = null,
    val source: VariableInfo? = null
): BaseExpression()