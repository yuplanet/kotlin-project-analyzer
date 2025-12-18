package org.example.data.symbol

import org.example.data.reference.MethodCallReference
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

data class ClassMethod(

    /**
     * <путь_к_файлу>.<имя_класса>::<имя_метода> ( параметры )
     *ApiKeyManagementController::getApiKeyClients():Boolean
     */
    val name: String,
    val fullName: String,
    val function: KtNamedFunction,
    val parameterTypeNames: List<String>,

    //content
    var properties: MutableList<ClassProperty> = mutableListOf(),
    var fullExpressions: List<FullExpression> = mutableListOf(),


    //params
    var ktParameters: List<KtParameter> = listOf(),

    //refs
    val callRecords: MutableList<MethodCallReference> = mutableListOf(),
    val reverseCallRecords: MutableList<MethodCallReference> = mutableListOf(),
)