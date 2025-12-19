package org.example.core.psi

import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.FullExpression
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*


object KtNamedFunctionExtractor {

    fun buildFullExpression(call: KtCallExpression, variable: String = "", type: String = "_"): FullExpression {

        val method = call.calleeExpression?.text ?: ""

        val receiver = (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text
            ?: (call.parent as? KtSafeQualifiedExpression)?.receiverExpression?.text
            ?: "this"
        val params = call.valueArguments.map { it.getArgumentExpression()?.text ?: "?" }


        return FullExpression(
            variable = variable,
            type = type,
            receiver = receiver,
            method = method,
            methodReturnType = "_",
            params = params
        )
    }

    fun collectFunctionExpressions(fn: KtNamedFunction, parentMethod: ClassMethod) {

        fun process(element: PsiElement, currentVar: String = "", currentType: String = "_") {
            when (element) {
                is KtProperty -> {
                    val varName = element.name ?: "__no_name__"
                    val varType = element.typeReference?.text ?: "_"

                    val classProp = ClassProperty(varName, varType, element)
                    parentMethod.properties += classProp

                    element.initializer?.let { init ->
                        if (init is KtCallExpression) {
                            val expr = buildFullExpression(init, varName, varType)
                            parentMethod.fullExpressions += expr
                        }
                        process(init, varName, varType) // передаем varName дальше
                    }
                }
                is KtCallExpression -> {
                    // Сначала рекурсивно обработаем аргументы, чтобы поймать вложенные вызовы
                    element.valueArguments.forEach { arg ->
                        arg.getArgumentExpression()?.let { process(it, currentVar, currentType) }
                    }

                    // Потом добавляем сам вызов в список
                    val expr = buildFullExpression(element, currentVar, currentType)
                    parentMethod.fullExpressions += expr
                }

                is KtDotQualifiedExpression -> element.selectorExpression?.let { process(it, currentVar, currentType) }
                is KtSafeQualifiedExpression -> element.selectorExpression?.let { process(it, currentVar, currentType) }
                is KtBlockExpression -> element.statements.forEach { process(it, currentVar, currentType) }
                is KtForExpression -> element.body?.let { process(it, currentVar, currentType) }
                is KtWhileExpression -> element.body?.let { process(it, currentVar, currentType) }
                is KtIfExpression -> {
                    element.then?.let { process(it, currentVar, currentType) }
                    element.`else`?.let { process(it, currentVar, currentType) }
                }
                is KtTryExpression -> {
                    process(element.tryBlock, currentVar, currentType)
                    element.catchClauses.forEach { it.catchBody?.let { process(it, currentVar, currentType) } }
                    element.finallyBlock?.finalExpression?.let { process(it, currentVar, currentType) }
                }

                else -> element.children.forEach { process(it, currentVar, currentType) }
            }
        }


        fn.bodyExpression?.let { process(it) }
    }
}