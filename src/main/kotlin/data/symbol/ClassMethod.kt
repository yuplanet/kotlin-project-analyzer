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
)
{
    var isApi: Boolean = false
    var apiUrl: String = ""
    var apiUri: String = ""

    var originalReturnType: String = returnType.replace("?","")
    val originalParameterTypeNames: List<String> = parameterTypeNames.map { it.replace("?","") }
    val signature: String
        get() = "${parentClass.fullName}.$name(${originalParameterTypeNames.joinToString(",")}):$originalReturnType"

    private fun initMethodApiData() {
        // сразу из KtNamedFunction
        val apiAnno = function.annotationEntries.filter { entry ->
            val name = entry.shortName?.asString() ?: return@filter false
            name in API_ANNOTATIONS_SHORT
        }

        if (apiAnno.isEmpty()) return

        isApi = true

        // путь метода — из первой аннотации с аргументом
        apiUrl = apiAnno.mapNotNull { entry ->
            entry.valueArguments.firstOrNull()?.getArgumentExpression()?.text?.trim('"')
        }.firstOrNull() ?: ""

        apiUri = parentClass.apiUrl + apiUrl

        print(apiUrl)
    }

    private fun extractPath(annotation: String): String? {
        // "..." аргумент без имени
        Regex("\"([^\"]+)\"").find(annotation)?.let { return it.groupValues[1] }

        // value = "..."
        Regex("value\\s*=\\s*\"([^\"]+)\"").find(annotation)?.let { return it.groupValues[1] }

        return null
    }
    companion object {
        private val API_ANNOTATIONS_SHORT = listOf(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping",
            "GET", "POST", "PUT", "DELETE", "PATCH", "Path"
        )
    }
    init {
        initMethodApiData()
    }
}