package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FullExpression
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.VariableInfo
import org.example.data.symbol.enum.ObjectType
import org.jetbrains.kotlin.psi.KtFile

class ExpressionTypeResolver(private val searchEngine: IProjectSearchEngine,
    private val currentClass: KotlinClass,
    private val currentMethod: ClassMethod) {

    private val ktFile: KtFile
    private var expressions: List<FullExpression>
    private val classVariables: MutableList<VariableInfo> = mutableListOf()
    private val methodVariables: MutableList<VariableInfo> = mutableListOf()

    init {

        expressions = currentMethod.fullExpressions
        ktFile = currentClass.ktFile

        // 1️⃣ Свойства класса
        currentClass.propertyReferences.forEach { prop ->
            classVariables.add(VariableInfo(name = prop.name, type = prop.type))
        }

        // 2️⃣ Параметры конструктора
        currentClass.parameterReferences.forEach { param ->
            classVariables.add(VariableInfo(name = param.name, type = param.type))
        }
    }


    fun getTypeByVariableName(variableName: String): String {

        var type = classVariables.firstOrNull { it.name == variableName }?.let {
            it.type
        }

        if (type == null)
            type = methodVariables.firstOrNull { it.name == variableName }?.let {
                it.type
            }
        if (type == null) {
            for (expression in expressions) {

                if (expression.target.name == variableName){
                    type = expression.target.type
                    return type                   
                }

                for (param in expression.method.parameters) {
                    if (param.name == variableName){
                        type = param.type
                        return type
                    }
                }
            }
        }

        if (type == null)
            type = getEnumOrObjectType(variableName)

        if (type == null)
            type = resolveTypeFromImports(variableName, ktFile)


        // 3️⃣ Если не нашли — неизвестно
        return type ?: "_"
    }


    // Функция для резолва полного имени класса через импорты
    private fun resolveTypeFromImports(typeName: String, ktFile: KtFile): String? {
        // Сначала ищем точное совпадение
        ktFile.importDirectives.firstOrNull { it.importedFqName?.shortName()?.asString() == typeName }
            ?.importedFqName?.asString()?.let { return it }

        // Затем ищем wildcard import, например java.time.*
        ktFile.importDirectives.firstOrNull { it.importedFqName?.asString()?.endsWith(".*") == true }
            ?.importedFqName?.asString()?.removeSuffix(".*")?.let { pkg ->
                return "$pkg.$typeName"
            }

        return null
    }
    
    
    fun resolveExpressionParameterType(expr: FullExpression) : String {


        val method = expr.method

        var type = if (method.rawContent.contains("(")) {
            val methodName = method.name.substringBefore("(")
            val type = searchEngine.findMethodByClassNameAndMethodNameAndParams(
                className = expr.target.name,
                methodName = methodName,
                params = method.parameters.map { it.type },

                )?.parameterTypeNames?.lastOrNull() ?: "_"

            type
        } else if (method.rawContent.contains(".")) {
            val member = method.name.substringAfter(".")

            val engineClass = searchEngine.findByClassName(expr.receiver.type)

            when (engineClass?.ktClassObjectType) {
                ObjectType.EnumClass -> expr.receiver.type

                ObjectType.Class -> {
                    engineClass.propertyReferences.firstOrNull { it.name == member }?.type
                        ?: engineClass.parameterReferences.firstOrNull { it.name == member }?.type
                        ?: "_"
                }

                else -> engineClass?.name ?: "_"
            }
        } else "_"

        if (type == "_") {
            // системный тип через импорты
            type = resolveTypeFromImports(expr.receiver.name, ktFile) ?: "_"
        }

        return type
    }

    private fun getEnumOrObjectType(param: String): String? {


         val type = if (param.contains(".")) {
            val className = param.substringBefore(".")
             val fieldName = param.substringAfter(".")

            val engineClass = searchEngine.findByClassName(className)

            when (engineClass?.ktClassObjectType) {
                ObjectType.EnumClass -> engineClass.name

                ObjectType.Class -> {
                    engineClass.propertyReferences.firstOrNull { it.name == fieldName }?.type
                        ?: engineClass.parameterReferences.firstOrNull { it.name == fieldName }?.type
                        ?: null
                }

                else -> engineClass?.name ?: null
            }
        } else null

        return type
    }

    fun getClassVariables(): List<VariableInfo> {
        return classVariables.toList()
    }

    fun getMethodVariables(): List<VariableInfo> {
        return methodVariables.toList()
    }
}