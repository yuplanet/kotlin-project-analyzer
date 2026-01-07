package org.example.data.symbol.expression

data class AssignmentExpression(
    var target: ExpressionValue?,                // VariableInfo / FieldInfo
    var source: ExpressionValue?,                // VariableInfo / FieldInfo / MethodInfo (receiver внутри MethodInfo)
) {
    var getRawExpression: String = ""
        get() = generateRawExpression()

    var getExpressionSignature: String = ""
        get() = generateExpressionSignature()

    private fun generateRawExpression(): String {
        if (target != null)
            return target!!.rawValue + " = " + source?.rawValue
        else
            return source?.rawValue ?: ""

    }

    private fun generateExpressionSignature(): String {
        if (target != null)
            return target!!.valueType + " = " + source?.valueType
        else
            return source?.valueType ?: ""
    }
}
