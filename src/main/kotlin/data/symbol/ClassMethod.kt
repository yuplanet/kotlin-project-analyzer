package org.example.data.symbol

import org.example.data.reference.ObjectReference
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

data class ClassMethod(

    /**
     * <путь_к_файлу>.<имя_класса>::<имя_метода> ( параметры )
     *ApiKeyManagementController::getApiKeyClients():Boolean
     */
    val name: String,
    val returnType: String,
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
    val callRecords: MutableList<ObjectReference> = mutableListOf(),
    val reverseCallRecords: MutableList<ObjectReference> = mutableListOf(),
)