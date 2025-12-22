package org.example.data.symbol

data class VariableInfo(
    var name: String = "",  // имя переменной
    var type: String = "",// её тип
    var isStatic: Boolean = false,
    var isEnum: Boolean = false,
)