package org.example.data.symbol.expression

data class MethodInfo (

    /**
     * Имя Метода
     */
    override var name: String = "",// её тип


    /**
     * Тип Метода
     */
    override var type: String = "",// её тип


    var parameters: MutableList<VariableInfo> = mutableListOf(),
    var rawContent: String = "",

    /**
     * Имя переменной, через которую вызывается метод
     */
    var receiverName: String = "",

    /**
     * тип переменной, через которую вызывается метод
     */
    var receiverClass: String = "",

): VariableInfo(name, type)


///