package org.example.data.symbol.expression

data class MethodValue (

    /**
     * Имя Метода
     */
     var methodName: String = "unknown",// её тип

    /**
     * Тип Метода
     */
    var methodReturnType: String = "unknown",// её тип


    var parameters: MutableList<ExpressionValue> = mutableListOf(),

    /**
     * Имя переменной, через которую вызывается метод
     */
    var receiverName: String = "unknown",
    var receiverClassName: String = "unknown",

    var innerCall: Boolean = false,


    var methodSignature: String = "unknown",
    override var rawValue: String = "",

): ExpressionValue(){

}