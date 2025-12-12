package org.example.core

import org.example.data.CallMethod
import org.example.data.KotlinClass
import org.example.data.ParamReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object CallBuilder {

    fun buildCallRecordsSimple(allClasses: List<KotlinClass>) {
        for (cls in allClasses) {
            analyzeFunctionCalls(cls, allClasses)
            analyzeFieldReferences(cls, allClasses)
        }

    }

    fun analyzeFunctionCalls(cls: KotlinClass, allClasses: List<KotlinClass>) {
        val allMethods = allClasses.flatMap { it.FunctionCalls }

        for (method in cls.FunctionCalls){
            val dotCalls = method.Function.collectDescendantsOfType<KtDotQualifiedExpression>()
            for (expr in dotCalls) {

                val receiver = expr.receiverExpression as? KtNameReferenceExpression
                val selector = expr.selectorExpression as? KtCallExpression

                if (receiver == null || selector == null)
                    continue

                val receiverName = receiver.getReferencedName()

                // Ищем поле по имени (ГОРАЗДО проще, чем твой fieldNamesToId)
                val fieldEntry = cls.Fields.entries.firstOrNull { it.value.name == receiverName }
                    ?: continue

                val calledMethodName = selector.calleeExpression?.text ?: continue

                // Находим метод по имени
                val target = allMethods.find { it.Function.name == calledMethodName }
                    ?: continue

                val parentClass = allClasses.first { it.FunctionCalls.contains(target) }

                method.CallRecords.add(
                    CallMethod(
                        CallMethodId = target.Id,
                        callMethodParentClass = parentClass
                    )
                )
            }
        }
    }

    fun analyzeFieldReferences(cls: KotlinClass, allClasses: List<KotlinClass>) {

        val allMethods = allClasses.flatMap { it.FunctionCalls }

        for ((fieldId, prop) in cls.Fields) {
            val propName = prop.name ?: continue

            // Найдём или создадим ParamReference
            val paramRef = cls.FieldsReferences
                .firstOrNull { it.Id == fieldId }
                ?: ParamReference(fieldId, prop).also {
                    cls.FieldsReferences.add(it)
                }

            for (method in allMethods) {

                val calls = method.Function.collectDescendantsOfType<KtCallExpression>()

                // проверяем, встречается ли propName среди аргументов
                val used = calls.any { call ->
                    call.valueArguments.any { arg ->
                        val argExpr = arg.getArgumentExpression()

                        when (argExpr) {
                            is KtNameReferenceExpression ->
                                argExpr.getReferencedName() == propName

                            is KtDotQualifiedExpression -> {
                                val last = argExpr.selectorExpression as? KtNameReferenceExpression
                                last?.getReferencedName() == propName
                            }

                            else -> false
                        }
                    }
                }

                if (used) {
                    val parentClass = allClasses.first { it.FunctionCalls.contains(method) }

                    paramRef.CallRecord.add(
                        CallMethod(
                            CallMethodId = method.Id,
                            callMethodParentClass = parentClass
                        )
                    )
                }
            }
        }
    }
}