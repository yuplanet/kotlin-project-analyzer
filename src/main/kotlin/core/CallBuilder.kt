package org.example.core

import org.example.data.CallMethod
import org.example.data.KotlinClass
import org.example.data.KotlinMethod
import org.example.data.ParamReference
import org.example.mapping.KotlinClassMapper
import org.example.mapping.KotlinClassMapper.getContainingClassName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object CallBuilder {

    fun buildCallRecordsSimple(allClasses: List<KotlinClass>) {
        for (cls in allClasses) {
            analyzeFunctionCalls(  allClasses)
            analyzeFieldReferences(cls, allClasses)
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


    fun analyzeFieldReferences(cls: KotlinClass, allClasses: List<KotlinClass>) {
    }
}