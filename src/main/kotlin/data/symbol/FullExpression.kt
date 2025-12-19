package org.example.data.symbol

data class FullExpression (

    var collingContext: String = "",                // имя переменной
    var collingContextType: String = "_",                   // тип переменной (можно по BindingContext или "_")

    var receiver: String = "",                  // объект/receiver
    var method: String = "",                  // имя метода
    var methodReturnType: String = "",        // имя метода
    var params: List<String> = listOf(),
)