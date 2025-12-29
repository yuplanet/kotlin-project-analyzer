package org.example.core.interfaces

import org.example.data.symbol.expression.AssignmentExpression
import org.example.data.symbol.expression.MethodInfo

interface IExpressionTypeResolver {


    //1 variable type
    //2 receiver type
    //3 method type

    // не может быть field, static
    fun getVariableType(variableName: String): String?

    fun getReceiverType(receiverName: String): String?

    fun getFieldType(className: String, fieldName: String): String?

    fun getMethodParameterType(param: String): String?

    fun getMethodOrFieldReturnType(expr: AssignmentExpression): String?

    fun getMethodReturnType(method: MethodInfo): String?
}