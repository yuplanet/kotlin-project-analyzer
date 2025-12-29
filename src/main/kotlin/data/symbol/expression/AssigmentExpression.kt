package org.example.data.symbol.expression

import org.example.data.symbol.enum.AssigmentExpressionType

data class AssignmentExpression(
    var target: VariableInfo?,                // VariableInfo / FieldInfo
    var source: VariableInfo,                // VariableInfo / FieldInfo / MethodInfo (receiver внутри MethodInfo)
    val operationType: AssigmentExpressionType,// VARIABLE_TO_VARIABLE, FIELD_TO_METHOD и т.д.
    val isParent: Boolean            // true для верхнего уровня инструкции
)
