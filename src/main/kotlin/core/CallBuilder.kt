package org.example.core

import org.example.data.*
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object CallBuilder {

    fun buildCallRecordsSimple(allClasses: List<KotlinClass>) {

        //1 calls
        //2reverse calls

        analyzeFunctionCalls(allClasses)
        buildReverseCallRecords(allClasses)

        //3fields
        //reversefields

    }

    fun buildReverseCallRecords(allClasses: List<KotlinClass>) {
        // 1. Глобальный индекс всех методов: fullName -> KotlinMethod
        val allMethodsByFullName: Map<String, KotlinMethod> =
            allClasses.flatMap { it.functionCalls }.associateBy { it.fullName }

        // 2. Очищаем обратные ссылки у всех методов
        allClasses.flatMap { it.functionCalls }.forEach { it.reverseCallRecords.clear() }

        // 3. Проходим по каждому методу и его прямым вызовам
        for (cls in allClasses) {
            for (method in cls.functionCalls) {
                for (call in method.callRecords) {
                    val calledMethod = allMethodsByFullName[call.callMethodFullName] ?: continue

                    // Добавляем текущий метод в reverseCallRecords вызываемого метода
                    calledMethod.reverseCallRecords.add(
                        ReverseCallMethod(
                            callerMethodFullName = method.fullName,
                            callerMethodParentClass = cls
                        )
                    )
                }
            }
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
                    val argParams: String = resolveArgumentTypes(selector, allFields, fn)
                        .joinToString(",")

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

    fun resolveArgumentTypes(
        selector: KtCallExpression,
        allFields: Map<String, KtCallableDeclaration>,
        fn: KtNamedFunction
    ): List<String> {
        return selector.valueArguments.map { arg ->
            val expr = arg.getArgumentExpression()
            when (expr) {
                is KtNameReferenceExpression -> {
                    val name = expr.getReferencedName()

                    // 1. Сначала проверяем параметры метода
                    val paramType = fn.valueParameters.find { it.name == name }?.typeReference?.text
                    if (paramType != null) return@map paramType

                    // 2. Проверяем поля класса
                    val fieldType = allFields[name]?.typeReference?.text
                    if (fieldType != null) return@map fieldType

                    // 3. Если не нашли — Any
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