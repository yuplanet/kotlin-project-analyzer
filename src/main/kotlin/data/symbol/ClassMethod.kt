package org.example.data.symbol

import org.example.data.symbol.expression.AssignmentExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

data class ClassMethod(

    /**
     * name
     * getApiKeyClients
     */
    val name: String,

    /**
     * type
     * Int or Unit
     */
    val returnType: String,

    /**
     * Class::Method( Params ): ReturnType
     * ApiKeyManagementController::getApiKeyClients():Boolean
     */
    val fullName: String,
    val function: KtNamedFunction,

    /**
     * List<param Typ>
     * List<Int,Int,Int>
     */
    val parameterTypeNames: List<String>,

    val parentClass: KotlinClass,
    //content
    var properties: MutableList<ClassProperty> = mutableListOf(),
    var parameters: MutableList<ClassParameter> = mutableListOf(),

    var fullExpressions: MutableList<AssignmentExpression> = mutableListOf(),

    //params
    var ktProperties: List<KtProperty> = listOf(),
    var ktParameters: List<KtParameter> = listOf(),

    //refs
    val callRecords: MutableList<ObjectReference> = mutableListOf(), // target method
    val reverseCallRecords: MutableList<ObjectReference> = mutableListOf(),
)