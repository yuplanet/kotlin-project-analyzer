package org.example.core.psi

import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.FullExpression
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*


object KtNamedFunctionExtractor {

    fun buildFullExpression(
        call: KtCallExpression,
        collingContext: String = "",
        collingContextType: String = "_",
        method: String = ""
    ): FullExpression {

        val receiver = (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text
            ?: (call.parent as? KtSafeQualifiedExpression)?.receiverExpression?.text
            ?: "this"
        val params = call.valueArguments.map { it.getArgumentExpression()?.text ?: "?" }

        return FullExpression(
            collingContext = collingContext,
            collingContextType = collingContextType,
            receiver = receiver,
            method = method.ifEmpty { call.calleeExpression?.text ?: "" },
            methodReturnType = "_",
            params = params
        )
    }


    fun collectFunctionExpressions(fn: KtNamedFunction, parentMethod: ClassMethod) {

        fun process(
            element: PsiElement,
            currentVar: String = "",
            currentType: String = "_",
            parentCall: PsiElement? = null
        ) {
            when (element) {
                is KtProperty -> {
                    val varName = element.name ?: "__no_name__"
                    val varType = element.typeReference?.text ?: "_"

                    parentMethod.properties += ClassProperty(varName, varType, element)

                    element.initializer?.let { init ->
                        process(init, varName, varType, parentCall)
                    }
                }

                is KtCallExpression -> {
                    // Сначала обрабатываем аргументы
                    element.valueArguments.forEach { arg ->
                        arg.getArgumentExpression()?.let { process(it, currentVar, currentType, element) }
                    }

                    // Определяем collingContext и method
                    val collingContextName = parentCall?.text ?: currentVar
                    val methodName = element.text

                    parentMethod.fullExpressions += buildFullExpression(
                        element,
                        collingContextName,
                        currentType,
                        methodName
                    )
                }


                is KtDotQualifiedExpression -> {
                    // Receiver становится parent для selector
                    element.receiverExpression?.let { process(it, currentVar, currentType, element) }
                    element.selectorExpression?.let { process(it, currentVar, currentType, element) }
                }

                is KtSafeQualifiedExpression -> {
                    element.receiverExpression?.let { process(it, currentVar, currentType, element) }
                    element.selectorExpression?.let { process(it, currentVar, currentType, element) }
                }

                is KtBlockExpression -> element.statements.forEach { process(it, currentVar, currentType, parentCall) }
                is KtForExpression -> element.body?.let { process(it, currentVar, currentType, parentCall) }
                is KtWhileExpression -> element.body?.let { process(it, currentVar, currentType, parentCall) }
                is KtIfExpression -> {
                    element.then?.let { process(it, currentVar, currentType, parentCall) }
                    element.`else`?.let { process(it, currentVar, currentType, parentCall) }
                }
                is KtTryExpression -> {
                    process(element.tryBlock, currentVar, currentType, parentCall)
                    element.catchClauses.forEach { it.catchBody?.let { process(it, currentVar, currentType, parentCall) } }
                    element.finallyBlock?.finalExpression?.let { process(it, currentVar, currentType, parentCall) }
                }

                else -> element.children.forEach { process(it, currentVar, currentType, parentCall) }
            }
        }



        fn.bodyExpression?.let { process(it) }
    }
}