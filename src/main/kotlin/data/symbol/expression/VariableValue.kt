package org.example.data.symbol.expression

class VariableValue(

    var variableName: String = "unknown",  // имя переменной
    var variableType: String = "unknown",// её тип

): ExpressionValue()