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
     * Class::Method( Params ): ReturnType
     * ApiKeyManagementController::getApiKeyClients():Boolean
     */
    val fullName: String,

    /**
     * Class.Method( Params ): ReturnType
     * ApiKeyManagementController::getApiKeyClients():Boolean
     */
    var signature: String = fullName,

    /**
     * Class.Method( Params ): ReturnType
     * ApiKeyManagementController::getApiKeyClients():Boolean
     */
    var originalSignature: String = fullName,

    val function: KtNamedFunction,

    /**
     * type
     * Int or Unit
     */
    val returnType: String,
    var originalReturnType: String = returnType,

    /**
     * List<param Typ>
     * List<Int,Int,Int>
     */
    var parameterTypeNames: List<String>,
    var originalParameterTypeNames: List<String> = parameterTypeNames,

    var annotations: List<String> = emptyList(),

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
) {
    var isApi: Boolean = false
    var apiUrl: String = ""
    var apiUri: String = ""

    private fun initMethodApiData() {
        // сразу из KtNamedFunction
        val apiAnno = function.annotationEntries.filter { entry ->
            val name = entry.shortName?.asString() ?: return@filter false
            name in API_ANNOTATIONS_SHORT
        }

        if (apiAnno.isEmpty()) return

        isApi = true

        // путь метода — из первой аннотации с аргументом
        apiUrl = apiAnno.firstNotNullOfOrNull { entry ->
            entry.valueArguments.firstOrNull()?.getArgumentExpression()?.text?.trim('"')
        } ?: ""

        apiUri = parentClass.apiUrl + apiUrl
    }

    companion object {
        private val API_ANNOTATIONS_SHORT = listOf(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping",
            "GET", "POST", "PUT", "DELETE", "PATCH", "Path"
        )
    }

    init {
        syncData()
        initMethodApiData()
    }

    fun syncData() {
        originalReturnType = returnType.replace("?", "")
        originalParameterTypeNames = parameterTypeNames.map { it.replace("?", "") }

        signature = "${parentClass.name}.$name(${parameterTypeNames.joinToString(",")}):$returnType"
        originalSignature =
            "${parentClass.name}.$name(${originalParameterTypeNames.joinToString(",")}):$originalReturnType"
    }
}