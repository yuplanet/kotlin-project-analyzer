package org.example.core

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.data.reference.MethodCallReference
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.example.data.symbol.ClassProperty
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File

class ClassReferenceBuilder : IClassReferenceBuilder {

    private var projectClasses = listOf<KotlinClass>()

    override fun initialize(_projectClasses: List<KotlinClass>) {
        projectClasses = _projectClasses
    }

    override fun bindAll() {
        // build method straight and reverse calls
        bindMethodCalls()
        dumpClassesAndMethods(projectClasses)

        // build parameter straight and reverse calls
        bindParameterCalls()

        // build field straight and reverse calls
        bindParameterCalls()
    }

    override fun bindMethodCalls() {

        //Methods
        analyzeFunctionCalls()
        buildReverseCallRecords()


        //properties
        bindPropertyCalls()


        //parameter
        bindParameterCalls()
    }

    override fun bindPropertyCalls() {
        for (cls in projectClasses) {
            val fieldsByName = cls.properties.associateBy { it.name ?: "__no_name__" }

            for (method in cls.functionCalls) {
                val fn = method.function
                val body = fn.bodyExpression ?: continue

                val nameRefs = body.collectDescendantsOfType<KtNameReferenceExpression>()

                for (ref in nameRefs) {
                    val propName = ref.getReferencedName()
                    val property = fieldsByName[propName] ?: continue

                    // Находим или создаём ClassProperty для этого свойства
                    val classProperty = cls.propertyReferences.firstOrNull { it.property == property }
                        ?: ClassProperty(property = property).also { cls.propertyReferences.add(it) }

                    // Добавляем ссылку на метод, который использует это свойство
                    classProperty.callRecord.add(
                        MethodCallReference(
                            fullName = method.fullName,
                            parentClass = cls
                        )
                    )
                }
            }
        }
    }

    override fun bindParameterCalls() {
        for (cls in projectClasses) {
            // Индекс параметров по имени
            val paramsByName = cls.parameters.associateBy { it.name ?: "__no_name__" }

            for (method in cls.functionCalls) {
                val fn = method.function
                val body = fn.bodyExpression ?: continue

                val nameRefs = body.collectDescendantsOfType<KtNameReferenceExpression>()

                for (ref in nameRefs) {
                    val paramName = ref.getReferencedName()
                    val parameter = paramsByName[paramName] ?: continue

                    // Находим или создаём ClassParameter
                    val classParam = cls.parameterReferences.firstOrNull { it.property == parameter }
                        ?: ClassParameter(property = parameter).also { cls.parameterReferences.add(it) }

                    // Добавляем ссылку на метод, который использует этот параметр
                    classParam.callRecord.add(
                        MethodCallReference(
                            fullName = method.fullName,
                            parentClass = cls
                        )
                    )
                }
            }
        }
    }

    private fun dumpClassesAndMethods(projectClasses: List<KotlinClass>) {
        val builder = StringBuilder()

        projectClasses.forEach { cls ->
            val className = cls.ktClassObject.fqName?.asString()
                ?: cls.ktClassObject.name
                ?: "<anonymous>"

            builder.appendLine("Class: $className")

            cls.functionCalls
                .sortedBy { it.fullName }
                .forEach { method ->
                    builder.appendLine("  Method: ${method.fullName}")

                    // ---- calls (кого вызывает этот метод)
                    if (method.callRecords.isNotEmpty()) {
                        builder.appendLine("    calls:")
                        method.callRecords.forEach { call ->
                            builder.appendLine("      -> ${call.fullName}")
                        }
                    }

                    // ---- callers (кто вызывает этот метод)
                    if (method.reverseCallRecords.isNotEmpty()) {
                        builder.appendLine("    callers:")
                        method.reverseCallRecords.forEach { reverse ->
                            builder.appendLine("      <- ${reverse.fullName}")
                        }
                    }
                }

            builder.appendLine()
        }

        File("class_methods_dump.txt").writeText(builder.toString())
    }


    fun buildReverseCallRecords() {
        // 1. Глобальный индекс всех методов: fullName -> KotlinMethod
        val allMethodsByFullName: Map<String, ClassMethod> =
            projectClasses.flatMap { it.functionCalls }.associateBy { it.fullName }

        // 2. Очищаем обратные ссылки у всех методов
        projectClasses.flatMap { it.functionCalls }.forEach { it.reverseCallRecords.clear() }

        // 3. Проходим по каждому методу и его прямым вызовам
        for (cls in projectClasses) {
            for (method in cls.functionCalls) {
                for (call in method.callRecords) {
                    val calledMethod = allMethodsByFullName[call.fullName] ?: continue

                    // Добавляем текущий метод в reverseCallRecords вызываемого метода
                    calledMethod.reverseCallRecords.add(
                        MethodCallReference(
                            fullName = method.fullName,
                            parentClass = cls
                        )
                    )
                }
            }
        }
    }

    fun findClassByReceiver(projectClasses: List<KotlinClass>, receiverName: String): KotlinClass? {
        return projectClasses.firstOrNull { cls ->
            cls.properties.any { it.name == receiverName } ||
                    cls.parameters.any { it.name == receiverName } ||
                    cls.superClasses.any { superCls ->
                        superCls.properties.any { it.name == receiverName } ||
                                superCls.parameters.any { it.name == receiverName }
                    }
        }
    }


    fun analyzeFunctionCalls() {

        // Глобальный индекс всех методов: fullName -> KotlinMethod
        val allMethodsByFullName: Map<String, ClassMethod> =
            projectClasses.flatMap { it.functionCalls }.associateBy { it.fullName }

        // Глобальный индекс классов по имени
        val classesByName: Map<String, KotlinClass> =
            projectClasses.associateBy { it.ktClassObject.name ?: "__anonymous__" }

        for (cls in projectClasses) {
            val allFields: Map<String, KtCallableDeclaration> =
                (cls.properties.asSequence().map { it as KtCallableDeclaration } +
                        cls.parameters.asSequence().map { it as KtCallableDeclaration })
                    .associateBy { it.name ?: "__no_name__" }

            for (method in cls.functionCalls) {
                val fn = method.function

                // Находим все выражения вида a.b(), obj.service.doWork(), и т.д.
                val dotCalls = fn.collectDescendantsOfType<KtDotQualifiedExpression>()

                callLoop@ for (expr in dotCalls) {

                    val receiver = expr.receiverExpression as? KtNameReferenceExpression
                        ?: continue@callLoop

                    val selector = expr.selectorExpression as? KtCallExpression
                        ?: continue@callLoop

                    val receiverName = receiver.getReferencedName()

                    // метод вызывается: a.method()
                    val calledMethodName = selector.calleeExpression?.text ?: continue@callLoop

                    // что такое a ?
                    val fieldDecl = allFields[receiverName] ?: continue@callLoop

                    // какой у него тип?
                    val fieldType = fieldDecl.typeReference?.text ?: continue@callLoop

                    // находим класс по имени типа
                    val targetClass = classesByName[fieldType] ?: continue@callLoop

                    // ----------- Строим полный ключ вызываемого метода -----------
                    // Параметры вызова (типов тут не узнать → Any)
                    val argParams: String = resolveArgumentTypes(selector, allFields, fn)
                        .joinToString(",")

                    val calledFullName =
                        "${targetClass.ktClassObject.name}::$calledMethodName($argParams)"

                    // Находим метод среди всех методов проекта
                    val targetMethod = allMethodsByFullName.values.find { method ->
                        method.fullName.contains(calledFullName)
                    } ?: continue@callLoop

                    // Добавляем в callRecords текущего метода
                    method.callRecords.add(
                        MethodCallReference(
                            fullName = targetMethod.fullName,
                            parentClass = targetClass
                        )
                    )
                }

                // ----------- 2. Вызовы методов текущего класса без this -----------
                // Берём только тело функции
                val body = fn.bodyExpression ?: continue
                val simpleCalls = body.collectDescendantsOfType<KtCallExpression>()
                    // исключаем аннотации
                    .filter { it.parent !is KtAnnotationEntry }

                for (callExpr in simpleCalls) {
                    // 1️⃣ только внутренние вызовы
                    val callee = callExpr.calleeExpression
                    if (callee !is KtNameReferenceExpression) continue

                    val calledMethodName = callee.text
                    val allFields: Map<String, KtCallableDeclaration> =
                        (cls.properties.asSequence().map { it as KtCallableDeclaration } +
                                cls.parameters.asSequence().map { it as KtCallableDeclaration })
                            .associateBy { it.name ?: "__no_name__" }

                    var methodArgs = resolveArgumentTypes(callExpr, allFields,fn)

                    val targetMethod = cls.functionCalls.firstOrNull { m ->
                        m.name == calledMethodName &&
                                m.parameters.values.map { it.typeReference?.text ?: "Any" } == methodArgs
                    } ?: continue

                    method.callRecords.add(
                        MethodCallReference(
                            fullName = targetMethod.fullName,
                            parentClass = cls
                        )
                    )
                }
            }
        }
    }

    fun resolveArgumentTypes(
        selector: KtCallExpression,
        allFields: Map<String, KtCallableDeclaration>,
        fn: KtNamedFunction
    ): List<String> {
        return selector.valueArguments.map { arg ->
            val expr = arg.getArgumentExpression()
            when (expr) {
                 is KtDotQualifiedExpression -> {
                    // Пример: EnvelopeStatus.SCHEDULED
                    val typeName = expr.receiverExpression.text
                    if (typeName.isNotBlank()) typeName else "Any"
                }

                is KtNameReferenceExpression -> {
                    val name = expr.getReferencedName()

                    // 1. Сначала проверяем параметры метода
                    val paramType = fn.valueParameters.find { it.name == name }?.typeReference?.text
                    if (paramType != null) return@map paramType

                    // 2. Проверяем поля класса
                    val fieldType = allFields[name]?.typeReference?.text
                    if (fieldType != null) return@map fieldType

                    // 3. Если не нашли — Any
                    val parent = expr.parent
                    if (parent is KtDotQualifiedExpression) {
                        val enumType = parent.receiverExpression.text
                        if (enumType.isNotBlank()) return@map enumType
                    }

                    // 4️⃣ По умолчанию Any
                    name // или "Any" если хочешь совсем безопасно
                }
                is KtCallExpression -> {
                    // Если передан вызов метода, пока можно использовать имя метода
                    expr.calleeExpression?.text ?: "Any"
                }
                is KtConstantExpression, is KtStringTemplateExpression -> {
                    // Литералы
                    when (expr) {
                        is KtConstantExpression -> expr.node.elementType.toString() // Int, Boolean и т.д.
                        else -> "String"
                    }
                }
                else -> "Any"
            }
        }
    }
}