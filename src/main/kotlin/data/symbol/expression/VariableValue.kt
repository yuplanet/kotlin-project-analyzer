package org.example.data.symbol.expression

class VariableValue(

    var variableName: String = "",  // имя переменной
    var variableType: String = "",// её тип

): ExpressionValue() {
    override var rawValue: String = ""
        get() = variableName

    override var valueType: String = ""
        get() = variableType

    var variableSignature: String = ""
        get() = variableType
}