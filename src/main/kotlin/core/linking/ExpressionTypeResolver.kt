package org.example.core.linking

import org.example.core.interfaces.IExpressionTypeResolver
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.enum.ObjectType
import org.example.data.symbol.expression.*
import org.jetbrains.kotlin.psi.KtFile

class ExpressionTypeResolver(
    private val searchEngine: IProjectSearchEngine,
    private val currentClass: KotlinClass,
    private val currentMethod: ClassMethod): IExpressionTypeResolver {

    private val ktFile: KtFile = currentClass.ktFile

    private var expressions: List<AssignmentExpression> = currentMethod.fullExpressions
    private val classVariables: MutableList<VariableValue> = mutableListOf()
    private val methodVariables: MutableList<VariableValue> = mutableListOf()

    init {

        // 1️⃣ Свойства класса
        currentClass.propertyReferences.forEach { prop ->
            classVariables.add(VariableValue(variableName = prop.name, variableType = prop.type))
        }

        // 2️⃣ Параметры конструктора
        currentClass.parameterReferences.forEach { param ->
            classVariables.add(VariableValue(variableName = param.name, variableType = param.type))
        }
    }

    override fun getVariableTypeByName(variableName: String): String? {

        var type: String? = null

        if (variableIsDotExpression(variableName)) {
            type = getEnumOrObjectTypeFromDotExpression(variableName)
            type?.let { return it }
        }

        var receive = variableName.replace("this.", "")

        //1 class property fields
        //2 method vars
        //3 imports, className
        //Члены класса
        type = findTypeInsideClass(receive)

        if (type == null)
            type = resolveTypeFromImports(receive, ktFile)

        // 3️⃣ Если не нашли — неизвестно
        return type
    }


    override fun getVariableTypeByNameAndClass(className: String, variableName: String): String? {

        val cl = searchEngine.findByClassName(className) ?: return null

        if (cl.ktClassObjectType == ObjectType.EnumClass)
            return className

        for (prop in cl.ktProperties) {

            if (prop.name == variableName)
                prop.typeReference?.text?.let { return it }
        }

        // Если не нашли в свойствах, ищем в параметрах KtParameter

        for (param in cl.ktParameters) {

            if (param.name == variableName)
                param.typeReference?.text?.let { return it }
        }
        return null
    }

    override fun getMethodReturnType(methodName: String, receiverClass: String, params: List<String>): String? {

        val methodClassName =
            if (receiverClass.isEmpty() || receiverClass == "this" || receiverClass == "this.") {
                currentClass.name
            } else {
                receiverClass
            }

        val type = searchEngine
            .findMethodByClassNameAndMethodNameAndParams(
                className = methodClassName,
                methodName = methodName,
                params = params
            )
            ?.returnType


        //to do
        return type
    }

    override fun getMethodReturnType(methodName: String, receiverClass: String, params: List<ExpressionValue>): String? {
        val methodClassName =
            if (receiverClass.isEmpty() || receiverClass == "this" || receiverClass == "this.") {
                currentClass.name
            } else {
                receiverClass
            }

        val type = searchEngine
            .findMethodByClassNameAndMethodNameAndParams(
                className = methodClassName,
                methodName = methodName,
                params = params.map { getExpressionValueTargetType(it) ?: "unknown" }
            )
            ?.returnType

        //to do
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

///////текущий класс

    private fun getEnumOrObjectTypeFromDotExpression(expression: String):String? {

        return if (expression.contains(".")) {
            var parentClass = expression.substringBefore(".")
            val fieldName = expression.substringAfter(".")

            if (parentClass == "this")
                parentClass = currentClass.name

            getEnumOrObjectTypeInsideClass(fieldName, parentClass)
        } else
            getEnumOrObjectTypeInsideClass(expression, currentClass.name)
    }


    //ищет тип static класса или поля класса
    private fun getEnumOrObjectTypeInsideClass(fieldName: String, className: String): String? {

        val engineClass = searchEngine.findByClassName(className) ?: return null

        if (engineClass.ktClassObjectType == ObjectType.EnumClass) {

            return engineClass.name

        } else if (engineClass.ktClassObjectType == ObjectType.Class) {

            var type = engineClass.propertyReferences.firstOrNull { it.name == fieldName }?.type

            if (type == null)
                type = engineClass.parameterReferences.firstOrNull { it.name == fieldName }?.type

            return type
        }
        return null
    }

    private fun variableIsDotExpression(variable: String): Boolean {
        return variable.contains(".")
    }

    private fun variableIsMethod(variable: String): Boolean {
        return variable.contains(".") && variable.contains("(")
    }


    //ищет тип внутри членов класса
    private fun findTypeInsideClass(variableName: String): String? {

        var type = classVariables.lastOrNull{ it.variableName == variableName }?.variableType

        if (type == null)
            type = methodVariables.lastOrNull { it.variableName == variableName }?.variableType

        if (type == null) {
            for (expression in expressions) {
                type = getVariableTypeFromExpression(expression, variableName)
                type?.let { return it }
            }
        }

        return type
    }

    //ищет тип внутри истории класса
    private fun getVariableTypeFromExpression(expression: AssignmentExpression, variableName: String): String? {

        var type: String? = null

        type = expression.target?.let {  getVariableTypeFromExpressionValue(it, variableName)}

        if (type == null)
            type = expression.source?.let {  getVariableTypeFromExpressionValue(it, variableName)}

        return type
    }

    //ищет тип внутри истории класса
    private fun getVariableTypeFromExpressionValue(target: ExpressionValue, variableName: String): String? {

        if (target is MethodValue) {

            for (parameter in target.parameters) {
                val type = getVariableTypeFromExpressionValue(parameter, variableName)
                type?.let { return it }
            }
        }

        else if (target is FieldValue) {

            if (target.fieldName == variableName)
                return target.fieldType
        }
        else if (target is VariableValue) {

            if (target.variableName == variableName)
                return target.variableType
        }

        return null
    }

    private fun getExpressionValueTargetType(expr: ExpressionValue): String? {

        if (expr is MethodValue) {

            if(!expr.methodReturnType.isNullOrEmpty() && expr.methodReturnType!="unknown")
                return expr.methodReturnType

            for (parameter in expr.parameters) {
                val type = getExpressionValueTargetType(parameter)
                type?.let { return it }
            }
        }
        else if (expr is FieldValue) {
            return expr.fieldType
        } else if (expr is VariableValue) {

            return expr.variableType
        }

        return null
    }
}