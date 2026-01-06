package org.example.data.symbol.expression

data class MethodValue (

    /**
     * Имя Метода
     */
     var methodName: String = "unknown",// её тип

    /**
     *  Возвращаемый тип
     */
    var methodReturnType: String = "unknown",// её тип


    var parameters: MutableList<ExpressionValue> = mutableListOf(),

    /**
     * Имя переменной, через которую вызывается метод
     */
    var receiverName: String = "unknown",

    /**
     * Тип переменной, через которую вызывается метод
     */
    var receiverClassName: String = "unknown",


    /**
     * Внутренний вызов? через This или без receiver
     */
    var innerCall: Boolean = false,

): ExpressionValue() {

    override var rawValue: String = "unknown"
        get() = "$receiverName.$methodName(${getRawParameters()})"

    override var valueType: String = "unknown"
        get() = methodReturnType

    var methodSignature: String = "unknown"
        get() = "$receiverClassName.$methodName(${getParameterSignatures()})"


    private fun getRawParameters(): String {

        if (parameters.isEmpty())
            return ""

        val raw: StringBuilder = StringBuilder()

        for (param in parameters) {
            raw.append(param.rawValue)
        }

        return raw.toString()
    }

    private fun getParameterSignatures(): String {
        if (parameters.isEmpty())
            return ""

        val raw: StringBuilder = StringBuilder()

        for (param in parameters) {
            raw.append(param.valueType)
        }

        return raw.toString()
    }
}