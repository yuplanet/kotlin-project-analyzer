package org.example.core.linking

import org.example.core.interfaces.IExpressionTypeResolver
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.enum.ObjectType
import org.example.data.symbol.expression.AssignmentExpression
import org.example.data.symbol.expression.FieldInfo
import org.example.data.symbol.expression.MethodInfo
import org.example.data.symbol.expression.VariableInfo
import org.jetbrains.kotlin.psi.KtFile

class ExpressionTypeResolver(

    private val searchEngine: IProjectSearchEngine,
    private val currentClass: KotlinClass,
    private val currentMethod: ClassMethod): IExpressionTypeResolver {

    private val ktFile: KtFile = currentClass.ktFile
    private var expressions: List<AssignmentExpression> = currentMethod.fullExpressions
    private val classVariables: MutableList<VariableInfo> = mutableListOf()
    private val methodVariables: MutableList<VariableInfo> = mutableListOf()

    init {

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

        var type: String? = null

        if (variableName.contains(".")) {

            type = getEnumOrObjectType(variableName)
            if (type != null)
                return type
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

    override fun getTypeByClassAndField(className: String, fieldName: String): String? {
        val cl = searchEngine.findByClassName(className) ?: return null

        if (cl.ktClassObjectType == ObjectType.EnumClass)
            return className

        for (prop in cl.ktProperties) {

            if (prop.name == fieldName)
                return prop.typeReference?.text ?: "_"
        }

        // Если не нашли в свойствах, ищем в параметрах KtParameter

        for (param in cl.ktParameters) {

            if (param.name == fieldName)
                return param.typeReference?.text ?: "_"
        }
        return null
    }



    override fun getMethodReturnType(method: MethodInfo): String? {

        // Определяем, какой класс для поиска метода
        val classNameForMethod =
            if (method.receiverName.isEmpty() || method.receiverName == "this" || method.receiverName == "this.") {
                currentClass.name
            } else {
                method.receiverName
            }

        // Ищем метод через searchEngine
        val type = searchEngine.findMethodByClassNameAndMethodNameAndParams(
            className = classNameForMethod,
            methodName = method.name,
            params = method.parameters.map { it.type }
        )?.returnType

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

    private fun getEnumOrObjectType(variable: String, className: String? =null):String? {

        return if (variable.contains(".")) {
            var parentClass = variable.substringBefore(".")
            val fieldName = variable.substringAfter(".")

            if (parentClass == "this")
                parentClass = currentClass.name


            getEnumOrObjectTypeInsideClass(fieldName, parentClass)
        } else
            getEnumOrObjectTypeInsideClass(variable, currentClass.name)

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


    //ищет тип внутри членов класса
    private fun findTypeInsideClass(variableName: String): String? {

        var type = classVariables.firstOrNull { it.name == variableName }?.type

        if (type == null)
            type = methodVariables.firstOrNull { it.name == variableName }?.type

        if (type == null) {
            for (expression in expressions) {
                type = getVariableTypeFromExpression(expression, variableName)
                if (type != null) break
            }
        }

        return type
    }

    //ищет тип внутри истории класса
    private fun getVariableTypeFromExpression(expression: AssignmentExpression, variableName: String): String? {
        val target = expression.target

        if (target is MethodInfo) {
            val method = expression.target as MethodInfo

            for (parameter in method.parameters) {
                if (parameter.name == variableName)
                    return parameter.type
            }
        } else if (target is FieldInfo) {
            val field = expression.target as FieldInfo

            if (field.name == variableName)
                return field.type
        } else if (target is VariableInfo) {

            if (target.name == variableName)
                return target.type
        }

        return null
    }
}