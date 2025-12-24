package org.example.data.symbol.expression

data class MethodInfo (

    override var name: String = "",// её тип
    override var type: String = "",// её тип


    var parameters: MutableList<VariableInfo> = mutableListOf(),
    var rawContent: String = "",

): VariableInfo(name, type)