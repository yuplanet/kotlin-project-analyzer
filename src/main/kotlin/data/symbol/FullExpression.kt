package org.example.data.symbol

data class FullExpression (

    var collingContext: VariableInfo = VariableInfo(),

    var receiver: String = "",                  // объект/receiver
    var method: String = "",                  // имя метода
    var methodReturnType: String = "",        // имя метода
    var params: List<VariableInfo> = listOf(),
)