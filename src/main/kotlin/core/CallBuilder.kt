package org.example.core

import org.example.data.CallMethod
import org.example.data.KotlinClass
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object CallBuilder {

    fun buildCallRecordsSimple(allClasses: List<KotlinClass>) {

        for (cls in allClasses) {

            // имена полей класса
            val fieldNames = cls.Fields.mapNotNull { it.name }

            for (method in cls.Methods) {

                // ищем вызовы вида repo.getUser()
                val dotCalls = method.function
                    .collectDescendantsOfType<KtDotQualifiedExpression>()

                for (expr in dotCalls) {

                    val receiver = expr.receiverExpression as? KtNameReferenceExpression
                    val selector = expr.selectorExpression as? KtCallExpression

                    if (receiver != null && selector != null) {

                        val receiverName = receiver.getReferencedName()

                        if (receiverName in fieldNames) {

                            val calledMethodName =
                                selector.calleeExpression?.text ?: continue

                            // ищем среди всех методов проекта по имени
                            val targetMethod = allClasses
                                .flatMap { it.Methods }
                                .find { it.function.name == calledMethodName }

                            if (targetMethod != null) {

                                val parentClass = allClasses
                                    .first { it.Methods.contains(targetMethod) }

                                method.CallRecords.add(
                                    CallMethod(
                                        CallMethodId = targetMethod.Id,
                                        callParentClass = parentClass
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}