package org.example.core.interfaces

import org.example.data.symbol.expression.ExpressionValue

interface IExpressionTypeResolver {

    //1 variable type
    //2 receiver type
    //3 method type

    // не может быть field, static
    /***
     * ищет тип внутри текущего класса
     */
    fun getVariableTypeByName(variableName: String): String?

    fun getVariableTypeByNameAndClass(className: String, variableName: String): String?

    fun getMethodReturnTypeByNameReceiveAndParamTypes(methodName: String, receiverClass: String, params: List<String>): String?

    fun getMethodReturnTypeByNameReceiveAndParamExpressions(methodName: String, receiverClass: String, params: List<ExpressionValue>): String?
}