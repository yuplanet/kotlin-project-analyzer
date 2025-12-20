package org.example.data.symbol

import org.example.data.reference.MethodCallReference
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

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
    var parameters: MutableList<ClassParameter> = mutableListOf(),

    var fullExpressions: MutableList<FullExpression> = mutableListOf(),

    //params
    var ktProperties: List<KtProperty> = listOf(),
    var ktParameters: List<KtParameter> = listOf(),

    //refs
    val callRecords: MutableList<MethodCallReference> = mutableListOf(),
    val reverseCallRecords: MutableList<MethodCallReference> = mutableListOf(),
)