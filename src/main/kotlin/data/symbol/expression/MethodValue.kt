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