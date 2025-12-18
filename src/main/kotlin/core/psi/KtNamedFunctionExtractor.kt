package org.example.core.psi

import org.example.data.symbol.Expression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType


object KtNamedFunctionExtractor {
    data class FunctionContent(
        val callSteps: List<Expression>,
        val assignments: List<String>
    )


    var tmpCounter = 0

    fun parseFunctionContent(fn: KtNamedFunction): Pair<List<Expression>, List<String>> {
        val expressions = mutableListOf<Expression>()
        val assignments = mutableListOf<String>()

        fun generateTmp(): String = "tmp${++tmpCounter}"

        // Рекурсивная обработка выражений
        fun processExpression(expr: KtExpression, variableName: String? = null): String {
            return when (expr) {
                is KtCallExpression -> {
                    val callee = expr.calleeExpression?.text ?: "unknown"

                    // Обрабатываем аргументы
                    val args = expr.valueArguments.map { arg ->
                        arg.getArgumentExpression()?.let { processExpression(it) } ?: "?"
                    }

                    // Определяем receiver
                    val parentReceiver = (expr.parent as? KtDotQualifiedExpression)?.receiverExpression?.text ?: "null"

                    // Используем явную переменную слева, если есть
                    val varName = variableName ?: generateTmp()

                    // Добавляем Expression
                    expressions.add(Expression().apply {
                        variable = varName
                        type = "_" // можно подставлять тип через BindingContext
                        receiver = parentReceiver
                        method = callee
                        params = args
                    })

                    varName
                }

                is KtDotQualifiedExpression -> {
                    val rec = processExpression(expr.receiverExpression)
                    expr.selectorExpression?.let { processExpression(it, rec) } ?: rec
                }

                else -> expr.text ?: ""
            }
        }

        // --- Обрабатываем локальные переменные (val/var) ---
        fn.collectDescendantsOfType<KtProperty>().forEach { prop ->
            val varName = prop.name ?: generateTmp()
            val initializer = prop.initializer
            val value = initializer?.let { processExpression(it, varName) } ?: "_"
            assignments.add("$varName = $value")
        }

        // --- Обрабатываем все вызовы без присваивания ---
        fn.collectDescendantsOfType<KtCallExpression>().forEach { call ->
            if (call.parent !is KtProperty) {
                processExpression(call)
            }
        }

        return expressions to assignments
    }
}