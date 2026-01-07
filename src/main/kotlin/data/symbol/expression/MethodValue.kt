package org.example.data.symbol.expression

data class MethodValue (

    /**
     * Имя Метода
     */
     var methodName: String = "",// её тип

    /**
     *  Возвращаемый тип
     */
    var methodReturnType: String = "",// её тип


    var parameters: MutableList<ExpressionValue> = mutableListOf(),

    /**
     * Имя переменной, через которую вызывается метод
     */
    var receiverName: String = "",

    /**
     * Тип переменной, через которую вызывается метод
     */
    var receiverClassName: String = "",


    /**
     * Внутренний вызов? через This или без receiver
     */
    var innerCall: Boolean = false,

): ExpressionValue() {

    override var rawValue: String = ""
        get() = "$receiverName.$methodName(${getRawParameters()})"

    override var valueType: String = ""
        get() = methodReturnType

    var methodSignature: String = ""
        get() = "$receiverClassName.$methodName(${getParameterSignatures()})"


    private fun getRawParameters(): String {

        return parameters.joinToString(",") { it.rawValue }
    }

    private fun getParameterSignatures(): String {
        return parameters.joinToString(",") { it.valueType }
    }

}