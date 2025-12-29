package org.example.data.symbol.expression

data class MethodInfo (

    /**
     * Имя Метода
     */
    override var name: String = "unknown",// её тип


    /**
     * Тип Метода
     */
    override var type: String = "unknown",// её тип


    var parameters: MutableList<VariableInfo> = mutableListOf(),
    var rawContent: String = "unknown",

    /**
     * Имя переменной, через которую вызывается метод
     */
    var receiverName: String = "unknown",

    /**
     * тип переменной, через которую вызывается метод
     */
    var receiverClass: String = "unknown",

): VariableInfo(name, type)


///