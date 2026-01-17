package org.example.core.search

import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FieldReference
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.enum.ObjectType
import org.example.data.symbol.expression.ExpressionValue
import org.example.data.symbol.expression.MethodValue
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker

class SearcherEngine: IProjectSearchEngine {

    private var allClasses = listOf<KotlinClass>()
    private var allMethods = listOf<ClassMethod>()

    //key - full name for class: path+name+args+return type
    private var classFullNameDictionary: MutableMap<Int, MutableList<KotlinClass>> = mutableMapOf()
    private var classSimpleNameDictionary: MutableMap<Int, MutableList<KotlinClass>> = mutableMapOf()

    //key - full name for method: name+args+return type
    private var methodSignatureDictionary: MutableMap<Int, MutableList<ClassMethod>> = mutableMapOf()
    private var methodOriginalSignatureDictionary: MutableMap<Int, MutableList<ClassMethod>> = mutableMapOf()

    override fun getAllMethods(): List<ClassMethod> {
        return allMethods
    }

    override fun getAllClasses(): List<KotlinClass> {
       return allClasses
    }

    override fun init(projectClasses: List<KotlinClass>) {
        allClasses = projectClasses
        allMethods = allClasses.flatMap { it.functionCalls }

        updateClassesHashes()

        updateMethodsHashes()
    }

    override fun updateMethodsHashes() {
        // Инициализация methodDictionary
        for (method in allMethods) {
            //ApiKeyManagementController::getApiKeyClients():ResponseEntity<List<ApiKeyClientResponseDto>>
            val key = method.signature.hashCode() // метод, который возвращает hashCode fullName
            methodSignatureDictionary.computeIfAbsent(key) { mutableListOf() }.add(method)

            val orKey = method.originalSignature.hashCode() // метод, который возвращает hashCode fullName
            methodOriginalSignatureDictionary.computeIfAbsent(orKey) { mutableListOf() }.add(method)
        }
    }

    override fun updateClassesHashes() {
        // Инициализация classDictionary
        for (cls in allClasses) {
            val key = cls.fullName.hashCode() // метод, который возвращает hashCode fullName
            classFullNameDictionary.computeIfAbsent(key) { mutableListOf() }.add(cls)

            val nameKey = cls.name.hashCode()
            classSimpleNameDictionary.computeIfAbsent(nameKey) { mutableListOf() }.add(cls)
        }
    }

    fun isSubTypeOfCustomType(parent: String, heir: String): Boolean {

        fun isParents(heirName: String, parentName: String): Boolean {
            val heirClass = findByClassName(parentName)
            val superClasses = heirClass?.superClasses ?: return false

            if (superClasses.any { it.name == heirName }) return true

            // рекурсивно проверяем супер-классы
            return superClasses.any { isParents(heirName, it.name) }
        }

        val isSubType = isParents(parent, heir)

        return isSubType
    }

    /***
     *
     */
    fun isSubTypeOfSysType(parent: String, heir: String): Boolean {
        val builtIns = DefaultBuiltIns.Instance

        fun getTypeByName(name: String): KotlinType? {
            val fq = when (name.lowercase()) {
                "int" -> "kotlin.Int"
                "string" -> "kotlin.String"
                "any" -> "kotlin.Any"
                "number" -> "kotlin.Number"
                else -> return null // кастомные классы возвращаем null
            }

            val descriptor = builtIns.getBuiltInClassByFqName(FqName(fq)) ?: return null
            return descriptor.defaultType
        }

        val parentType = getTypeByName(parent) ?: return false
        val heirType = getTypeByName(heir) ?: return false

        return KotlinTypeChecker.DEFAULT.isSubtypeOf(heirType, parentType)
    }


    override fun findFieldRefByClassNameAndFieldName(
        className: String,
        fieldName: String
    ): FieldReference? {

        val ktClass = findByClassName(className)
            ?:return null

        val field = ktClass.parameterReferences.firstOrNull {
            it.name == fieldName
        }

        if (field != null) {
            val reference = FieldReference(
                referenceTargetName = fieldName,
                referenceTargetParentClass = ktClass,
                signature = "",
                expressionValue = null
            )
            //(variableName = fieldName, variableType = field.type))
            return reference
        }

        val paramd = ktClass.propertyReferences.firstOrNull {
            it.name == fieldName
        }
        if (paramd != null) {
            val reference = FieldReference(
                referenceTargetName = fieldName,
                referenceTargetParentClass = ktClass,
                signature = "",
                expressionValue = null
            )
            return reference
        }

        return null
    }
    //Methods

    override fun findByFullMethodName(methodName: String): ClassMethod? {

        val key = methodName.hashCode()

        val candidates = methodSignatureDictionary[key] ?: methodOriginalSignatureDictionary[key] ?: emptyList()

        val result = candidates.firstOrNull { method ->
            // проверяем класс
            method.fullName == methodName
        }

        return result
    }

    override fun isEnumClass(className: String): Boolean {

        val ktClass = findByClassName(className) ?: return false

        return ktClass.ktClassObjectType == ObjectType.EnumClass
    }

    override fun isStaticClass(className: String): Boolean {

        val ktClass = findByClassName(className) ?: return false

        return ktClass.ktClassObjectType == ObjectType.Object
    }


    //Classes
    override fun findByClassName(className: String): KotlinClass? {

        val _className =  className.replace("?", "")

        val key = _className.hashCode()
        val candidates = classSimpleNameDictionary[key] ?: emptyList()

        val result = candidates.firstOrNull { it.ktClassObject.name == className }

        return result
    }


    //class and method
    override fun findByClassNameAndMethodName(className: String, methodName: String): List<ClassMethod> {
        val targetClass = findByClassName(className) ?: return emptyList()

        val matchedMethod = targetClass.functionCalls.filter { method ->
            method.function.name == methodName
        }

        return matchedMethod
    }

    override fun findFirstMethodByClassNameAndMethodName(className: String, methodName: String): ClassMethod? {

        val parentClass = findByClassName(className) ?: return null

        val result = parentClass.functionCalls.firstOrNull { method -> method.name == methodName }
        return result
    }

    ///118
    override fun findMethodByClassNameAndMethodNameAndParams(className: String, methodName: String, params: List<String>): ClassMethod? {

        val candidates = findByClassNameAndMethodName(className, methodName)

        val result = candidates.firstOrNull { method -> areParamsEqual(params, method.parameterTypeNames) }

        return result
    }

    override fun findAllMethodByClassNameAndMethodNameAndParams(className: String, methodName: String, params: List<String>): List<ClassMethod> {

        val methods = mutableListOf<ClassMethod>()

        // Собираем все классы: исходный + родители + дети
        val allClasses = (
                getParentClasses(className) +
                getChildClasses(className) +
                className).toSet() // Set — избегаем дубликатов

        // Ищем метод в каждом классе
        for (cls in allClasses) {
            val method = findMethodByClassNameAndMethodNameAndParams(cls, methodName, params)
            method?.let { methods.add(it) }
        }

        return methods
    }


    override fun findMethodByClassNameAndMethodNameAndParamsCount(className: String, methodName: String, paramsCount: Int): ClassMethod? {
        val parentClass = findByClassName(className) ?: return null

        val candidates = parentClass.functionCalls.filter { it.name == methodName }

        val results = candidates.filter { method -> method.parameterTypeNames.count() == paramsCount }

        if (results.count() == 1)
            return results.first()
        // проверяем параметры

        return null
    }

    /**
     * class + method()type
     * */
    override fun findMethodByClassNameAndFullMethodName(className: String, methodFullName: String): ClassMethod? {

        val parentClass = findByClassName(className) ?: return null
        val method = parentClass.functionCalls.firstOrNull { it.fullName.contains("::${methodFullName}") }
        return method
    }

    /**
     * class.method()type
     * */
    override fun findMethodByFullName(expression: String): ClassMethod? {

        val methodClassName = expression.substringBefore("::")

        val fullMethodExpression = expression.substringAfter("::")
        val method =  findMethodByClassNameAndFullMethodName(methodClassName, fullMethodExpression)

        return method
    }

    override fun findMethodBySignature(signature: String): ClassMethod? {
        val methodClassName = signature.substringBefore(".")

        val fullMethodExpression = signature.substringAfter(".")
        val method =  findMethodByClassNameAndFullMethodName(methodClassName, fullMethodExpression)

        return method
    }

    override fun findAllMethodByClassNameAndFullMethodName(
        className: String,
        methodFullName: String
    ): List<ClassMethod> {

        val calls = mutableListOf<ClassMethod>()

        val method = findMethodByClassNameAndFullMethodName(className, methodFullName)
        method?.let { calls.add(it) }

        //call in super classes
        val methodClass = method?.parentClass

        if (methodClass != null) {

            for (superClass in methodClass.superClasses) {
                val parentMethod = findMethodByClassNameAndFullMethodName(superClass.name, methodFullName)
                parentMethod?.let { calls.add(it) }
            }

            for (subClass in methodClass.subClasses) {
                val subClassMethod = findMethodByClassNameAndFullMethodName(subClass.name, methodFullName)
                subClassMethod?.let { calls.add(it) }
            }
        }

        return calls
    }

    override fun findCallerMethodsByMethod(method: ClassMethod): List<ClassMethod> {

        fun isCallOf(expr: ExpressionValue?, target: ClassMethod): Boolean {
            val mv = expr as? MethodValue ?: return false

            val mvHash = mv.methodSignature.hashCode()
            val methodHash = method.signature.hashCode()
            if(mvHash != methodHash) return false
            // проверка имени метода

            if (mv.methodName == target.name
                && mv.receiverClassName == target.parentClass.name
                && areParamsEqual(mv.parameters.map { it.valueType }, target.parameterTypeNames))
                return true

            return false
        }

            val result = mutableListOf<ClassMethod>()

            for (cls in allClasses) {
                for (classMethod in cls.functionCalls) {

                    for (expr in classMethod.fullExpressions) {

                        if (isCallOf(expr.target, method) || isCallOf(expr.source, method)) {
                            result.add(classMethod)
                            break
                        }
                    }
                }
            }

            return result
    }

    // utils
    /**
     * Проверяет, совпадают ли два списка типов аргументов.
     * Возвращает true, если списки одной длины и все элементы на соответствующих позициях равны.
     */

    private fun areParamsEqual(
        targetParams: List<String>,
        methodParams: List<String>
    ): Boolean {
        if (targetParams.size != methodParams.size) return false

        for (i in targetParams.indices) {
            val parent = targetParams[i].replace("?", "")
            val heir = methodParams[i].replace("?", "")


            if (parent == heir)
                continue
            else {
                val isSysSubType = isSubTypeOfSysType(parent, heir)

                if (isSysSubType == false) {
                    val isCustomSubType = isSubTypeOfCustomType(parent, heir)

                    if (isCustomSubType)
                        continue
                    else
                        return false
                }
            }
        }
        return true
    }

    private fun getParentClasses(
        className: String,
        parents: MutableSet<String> = mutableSetOf()
    ): Set<String> {
        val currentClass = findByClassName(className) ?: return parents

        if (!parents.add(currentClass.name))
            return parents // уже были — защита от циклов

        for (parent in currentClass.superClasses)
            getParentClasses(parent.name, parents)

        return parents
    }

    private fun getChildClasses(
        className: String,
        children: MutableSet<String> = mutableSetOf()
    ): Set<String> {
        val currentClass = findByClassName(className) ?: return children

        if (!children.add(currentClass.name))
            return children // уже были — защита от циклов

        for (child in currentClass.subClasses)
            getChildClasses(child.name, children)

        return children
    }
}