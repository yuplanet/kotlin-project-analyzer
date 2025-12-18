package org.example.data.symbol

class Expression {
    var variable: String = ""     // имя переменной
    var type: String = "_"         // тип переменной (можно по BindingContext или "_")
    var receiver: String = ""      // объект/receiver
    var method: String = ""        // имя метода
    var params: List<String> = listOf()
}