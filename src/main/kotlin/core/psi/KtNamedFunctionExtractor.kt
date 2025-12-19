package org.example.core.psi

import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.linking.ExpressionTypeResolver
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.FullExpression
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.VariableInfo
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*

object KtNamedFunctionExtractor {


    var tmpCounter = 1

    fun buildFullExpression(
        call: KtCallExpression,
        collingContext: String = "",
        collingContextType: String = "_",
        method: String = "",
        fullExpressions: List<FullExpression>
    ): FullExpression {

        val receiverExpression = (call.parent as? KtDotQualifiedExpression)?.receiverExpression
            ?: (call.parent as? KtSafeQualifiedExpression)?.receiverExpression

        val receiverName = when (receiverExpression) {
            is KtNameReferenceExpression -> receiverExpression.getReferencedName() // просто имя переменной/объекта
            is KtThisExpression -> "this"
            else -> "tmp${tmpCounter++}" // если это сложное выражение → временный контекст
        }


        val params = call.valueArguments.map {

            val paramName = it.getArgumentExpression()?.text ?: "?"

            val typeFromFullExpr = fullExpressions
                .firstOrNull { it.collingContext.name == paramName }
                ?.collingContext?.type
            // 2️⃣ Если не нашли, ищем в params этих FullExpression
                ?: fullExpressions.firstOrNull { expr ->
                    expr.params.any { it.name == paramName }
                }?.params?.firstOrNull { it.name == paramName }?.type
                ?: "" // оставляем пустым, если нигде не нашли

            VariableInfo(paramName, typeFromFullExpr)
        }

        return FullExpression(
            collingContext = VariableInfo(collingContext, collingContextType),
            receiver = receiverName,
            method = method.ifEmpty { call.calleeExpression?.text ?: "" },
            methodReturnType = "_",
            params = params
        )
    }


    fun collectFunctionExpressions(fn: KtNamedFunction,
                                   parentMethod: ClassMethod,
                                   parentClass: KotlinClass,
                                   engine: IProjectSearchEngine,
    ) {






















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
                    // Сначала обрабатываем аргументы рекурсивно
                    element.valueArguments.forEach { arg ->
                        arg.getArgumentExpression()?.let { process(it, "", currentType, element) }
                    }

                    // Определяем calling context
                    val collingContextName = currentVar.ifEmpty {
                        "tmp${tmpCounter++}"
                    }
                    // Имя метода — сам вызов
                    val methodName = element.calleeExpression?.text ?: ""

                    val expression = buildFullExpression(
                        element,
                        collingContextName,
                        currentType,
                        methodName,
                        parentMethod.fullExpressions
                    )

                    parentMethod.fullExpressions += expression
                    ExpressionTypeResolver.resolveExpressionType(expression, parentMethod, parentClass, engine)

                    print(1)
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