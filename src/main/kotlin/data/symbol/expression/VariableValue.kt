package org.example.data.symbol.expression

class VariableValue(

    var variableName: String = "unknown",  // имя переменной
    var variableType: String = "unknown",// её тип

): ExpressionValue() {
    override var rawValue: String = "unknown"
        get() = variableName

    override var valueType: String = "unknown"
        get() = variableType

    var variableSignature: String = "unknown"
        get() = variableType
}