package org.example.data.symbol.expression

data class AssignmentExpression(
    var target: ExpressionValue?,                // VariableInfo / FieldInfo
    var source: ExpressionValue?,                // VariableInfo / FieldInfo / MethodInfo (receiver внутри MethodInfo)
)
