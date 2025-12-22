package org.example.data.symbol

data class MethodInfo (
    var name: String ="",
    var returnType: String = "",

    var parameters: MutableList<VariableInfo> = mutableListOf(),

    var innerInvoke: Boolean = false,
    var rawContent: String = "",
)