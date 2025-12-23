package org.example.core.linking

import org.example.core.interfaces.IExpressionTypeResolver
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FieldInfo
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.VariableInfo
import org.example.data.symbol.enum.ObjectType
import org.example.data.symbol.expression.*
import org.jetbrains.kotlin.psi.KtFile

class ExpressionTypeResolver(
    private val searchEngine: IProjectSearchEngine,
    private val currentClass: KotlinClass,
    private val currentMethod: ClassMethod): IExpressionTypeResolver {

    private val ktFile: KtFile
    private var expressions: List<BaseExpression>
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

    override fun getVariableType(variableName: String): String? {

        var type = classVariables.firstOrNull { it.name == variableName }?.type

        if (type == null)
            type = methodVariables.firstOrNull { it.name == variableName }?.type

        if (type == null) {
            for (expressionBase in expressions) {
                getTypeFromBaseExpression(expressionBase, variableName)
            }
        }

        return type
    }

    private fun getTypeFromBaseExpression(expressionBase: BaseExpression, variableName: String): String? {

        if (expressionBase is VariableAssignmentExpression) {
            val expression = expressionBase as VariableAssignmentExpression

            if (expression.target != null) {

                var variableType = getVariableTypeFromVariableInfo(expression.target!!, variableName)
                if (variableType != null)
                    return variableType
            }


            for (param in expression.method.parameters) {
                val variableType = getVariableTypeFromVariableInfo(param, variableName)
                if (variableType != null)
                    return variableType
            }
        } else if (expressionBase is FieldAssignmentExpression) {
            val expression = expressionBase as FieldAssignmentExpression

            if (expression.target != null) {
                val variableType = getVariableTypeFromFieldInfo(expression.target!!, variableName)
                if (variableType != null)
                    return variableType
            }

            if (expression.source != null) {

                val variableType = getVariableTypeFromVariableInfo(expression.source, variableName)
                if (variableType != null)
                    return variableType
            } else if (expressionBase is FieldToFieldAssignmentExpression) {
                val expression = expressionBase as FieldToFieldAssignmentExpression
                if (expression.target != null) {
                    val variableType = getVariableTypeFromFieldInfo(expression.target!!, variableName)
                    if (variableType != null)
                        return variableType
                }
                if (expression.source != null) {
                    val variableType = getVariableTypeFromFieldInfo(expression.source, variableName)
                    if (variableType != null)
                        return variableType
                }
            } else if (expressionBase is VariableToFieldAssignmentExpression) {
                val expression = expressionBase as VariableToFieldAssignmentExpression
                if (expression.target != null) {
                    val variableType = getVariableTypeFromVariableInfo(expression.target, variableName)
                    if (variableType != null)
                        return variableType
                }
                if (expression.source != null) {
                    val variableType = getVariableTypeFromFieldInfo(expression.source, variableName)
                    if (variableType != null)
                        return variableType
                }
            } else
                return null
        }
        return null
    }


    private fun getVariableTypeFromFieldInfo(field: FieldInfo, variableName: String): String? {
        val tp =
            if (field.name == variableName)
                field.type
            else null

        return tp
    }


    private fun getVariableTypeFromVariableInfo(variableInfo: VariableInfo, variableName: String): String? {
        val tp =
            if (variableInfo.name == variableName)
                variableInfo.type
            else null

        return tp
    }



    override fun getReceiverType(variableName: String): String? {

        var receive = variableName.replace("this.", "")
        //1 class property fields
        //2 method vars
        //3 imports, className
        //Члены класса
        var type = getVariableType(receive)

        if (type == null)
            type = resolveTypeFromImports(receive, ktFile)

        if (type == null)
            type = getByClassName(receive)

        // 3️⃣ Если не нашли — неизвестно
        return type
    }

    override fun getFieldType(className: String, fieldName: String): String? {
        val cl = searchEngine.findByClassName(className)

        if (cl == null)
            return null

        else if (cl.ktClassObjectType == ObjectType.EnumClass)
            return className

        else {
            var foundType: String? = null

            // Сначала ищем в свойствах KtProperty

            for (prop in cl.ktProperties) {
                if (prop.name == fieldName) {
                    foundType = prop.typeReference?.text ?: "_"
                    break
                }
            }

            // Если не нашли в свойствах, ищем в параметрах KtParameter

            if (foundType == null) {
                for (param in cl.ktParameters) {
                    if (param.name == fieldName) {
                        foundType = param.typeReference?.text ?: "_"
                        break
                    }
                }
            }
            return foundType

        }
    }

    override fun getMethodParameterType(param: String): String? {
        var type = getVariableType(param)

        if (type == null)
            type = getEnumOrObjectType(param)

        return  type

    }

    //возвращает тип по методу с переменной вида
    /// 1 class.Method(var)
    /// 2 class.Method()
    /// 3 class.field
    override fun getMethodOrFieldReturnType(expr: VariableAssignmentExpression): String? {

        val method = expr.method

        var type = if (method.rawContent.contains("(")) {
            val methodName = method.name.substringBefore("(")

            val type = if(expr.receiver == null)
            {
                searchEngine.findMethodByClassNameAndMethodNameAndParams(
                    className = currentClass.name,
                    methodName = methodName,
                    params = method.parameters.map { it.type },

                    )?.returnType
            }
            else{
                searchEngine.findMethodByClassNameAndMethodNameAndParams(
                    className = expr.receiver.type,
                    methodName = methodName,
                    params = method.parameters.map { it.type },

                    )?.returnType
            }



            type
        } else if (method.rawContent.contains(".")) {
            val member = method.name.substringAfter(".")

            var classType = expr.receiver?.type?: currentClass.name

            val methodClass = searchEngine.findByClassName(classType)

            when (methodClass?.ktClassObjectType) {
                ObjectType.EnumClass -> classType

                ObjectType.Class -> {
                    methodClass.propertyReferences.firstOrNull { it.name == member }?.type
                        ?: methodClass.parameterReferences.firstOrNull { it.name == member }?.type
                }

                else -> null
            }
        } else null

        if (type == null) {
            // системный тип через импорты
            type  = expr.receiver?.let {resolveTypeFromImports(it.name , ktFile) }
            type = type ?: "_"
        }

        return type
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
                }

                else -> null
            }
        } else null

        return type
    }

    fun getByClassName(className: String): String? {
        return searchEngine.findByClassName(className)?.name
    }
}