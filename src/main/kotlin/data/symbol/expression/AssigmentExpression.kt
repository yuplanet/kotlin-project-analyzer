package org.example.data.symbol.expression

import org.example.data.symbol.enum.AssigmentExpressionType

data class AssignmentExpression(
    var target: ExpressionValue?,                // VariableInfo / FieldInfo
    var source: ExpressionValue?,                // VariableInfo / FieldInfo / MethodInfo (receiver внутри MethodInfo)
    val operationType: AssigmentExpressionType = AssigmentExpressionType.Undefined,// VARIABLE_TO_VARIABLE, FIELD_TO_METHOD и т.д.
)
