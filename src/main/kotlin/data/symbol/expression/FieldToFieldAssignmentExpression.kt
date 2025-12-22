package org.example.data.symbol.expression

import org.example.data.symbol.FieldInfo

data class FieldToFieldAssignmentExpression (
    var target: FieldInfo? = null,
    val source: FieldInfo? = null
): BaseExpression()