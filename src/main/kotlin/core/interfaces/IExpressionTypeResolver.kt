package org.example.core.interfaces

import org.example.data.symbol.expression.MethodInfo

interface IExpressionTypeResolver {

    //1 variable type
    //2 receiver type
    //3 method type

    // не может быть field, static
    /***
     * ищет тип внутри текущего класса
     */
    fun getVariableType(variableName: String): String?

    fun getTypeByClassAndField(className: String, fieldName: String): String?

    fun getMethodReturnType(method: MethodInfo): String?
}