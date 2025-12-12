package org.example.core

import org.example.data.*
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object CallBuilder {

    fun buildCallRecordsSimple(allClasses: List<KotlinClass>) {
        for (cls in allClasses) {
            analyzeFunctionCalls(allClasses)
                //analyzeClassMembersReferences(cls, allClasses)
        }
    }

    fun analyzeFunctionCalls(allClasses: List<KotlinClass>) {

        // Глобальный индекс всех методов: fullName -> KotlinMethod
        val allMethodsByFullName: Map<String, KotlinMethod> =
            allClasses.flatMap { it.functionCalls }.associateBy { it.fullName }

        // Глобальный индекс классов по имени
        val classesByName: Map<String, KotlinClass> =
            allClasses.associateBy { it.ktClassObject.name ?: "__anonymous__" }

        for (cls in allClasses) {
            val allFields: Map<String, KtCallableDeclaration> =
                (cls.fields.asSequence().map { it as KtCallableDeclaration } +
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
                    val argParams = selector.valueArguments
                        .joinToString(",") { "Any" }

                    val calledFullName =
                        "${targetClass.ktClassObject.name}::$calledMethodName($argParams)"

                    // Находим метод среди всех методов проекта
                    val targetMethod = allMethodsByFullName[calledFullName]
                        ?: continue@callLoop

                    // Добавляем в callRecords текущего метода
                    method.callRecords.add(
                        CallMethod(
                            callMethodFullName = targetMethod.fullName,
                            callMethodParentClass = targetClass
                        )
                    )
                }
            }
        }
    }

    fun analyzeClassMembersReferences(targetClass: KotlinClass, allClasses: List<KotlinClass>) {

        val className = targetClass.ktClassObject.name ?: return

        // --- 1. Обрабатываем поля ---
        targetClass.fields.forEach { field ->

            val instanceNames = mutableSetOf("this")

            // Находим параметры методов во всех классах, которые ссылаются на targetClass
            for (cls in allClasses) {
                for (method in cls.functionCalls) {
                    method.function.valueParameters.forEach { param ->
                        val type = param.typeReference?.text ?: return@forEach
                        if (type.contains(className)) instanceNames += param.name!!
                    }
                }
            }

            // Ищем все dot-qualified вызовы
            for (cls in allClasses) {
                for (method in cls.functionCalls) {
                    val dotCalls = method.function.collectDescendantsOfType<KtDotQualifiedExpression>()
                    dotCallsLoop@ for (expr in dotCalls) {
                        val text = expr.text
                        for (instance in instanceNames) {
                            val pattern = "$instance.${field.name}"
                            if (text.contains(pattern)) {
                                val ref = targetClass.fieldReferences
                                    .firstOrNull { it.property == field }
                                    ?: FieldReference(field).also { targetClass.fieldReferences += it }

                                ref.callRecord += CallMethod(
                                    callMethodFullName = method.fullName,
                                    callMethodParentClass = cls
                                )
                                continue@dotCallsLoop
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Обрабатываем параметры конструктора ---
        targetClass.parameters.forEach { param ->

            val instanceNames = mutableSetOf("this")

            for (cls in allClasses) {
                for (method in cls.functionCalls) {
                    method.function.valueParameters.forEach { p ->
                        val type = p.typeReference?.text ?: return@forEach
                        if (type.contains(className)) instanceNames += p.name!!
                    }
                }
            }

            for (cls in allClasses) {
                for (method in cls.functionCalls) {
                    val dotCalls = method.function.collectDescendantsOfType<KtDotQualifiedExpression>()
                    dotCallsLoop@ for (expr in dotCalls) {
                        val text = expr.text
                        for (instance in instanceNames) {
                            val pattern = "$instance.${param.name}"
                            if (text.contains(pattern)) {
                                val ref = targetClass.parameterReferences
                                    .firstOrNull { it.property == param }
                                    ?: ParamReference(param).also { targetClass.parameterReferences += it }

                                ref.callRecord += CallMethod(
                                    callMethodFullName = method.fullName,
                                    callMethodParentClass = cls
                                )
                                continue@dotCallsLoop
                            }
                        }
                    }
                }
            }
        }
    }
}