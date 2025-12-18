package org.example.core.psi

import org.example.data.symbol.Expression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType


object KtNamedFunctionExtractor {
    private var tmpCounter = 0

    fun parseFunctionContent(
        fn: KtNamedFunction
    ): Pair<List<Expression>, List<String>> {

        val expressions = mutableListOf<Expression>()
        val assignments = mutableListOf<String>()

        fun generateTmp(): String = "tmp${++tmpCounter}"

        /**
         * @param expr анализируемое выражение
         * @param assignedVar имя переменной, КУДА пишется результат (val x = ...)
         */
        fun processExpression(
            expr: KtExpression,
            assignedVar: String? = null
        ): String =
            when (expr) {

                is KtCallExpression -> {
                    val methodName = expr.calleeExpression?.text ?: "unknown"

                    val params = expr.valueArguments.map { arg ->
                        arg.getArgumentExpression()
                            ?.let { processExpression(it) }
                            ?: "?"
                    }

                    val receiver =
                        (expr.parent as? KtDotQualifiedExpression)
                            ?.receiverExpression
                            ?.text
                            ?: ""

                    val variable = assignedVar ?: generateTmp()

                    expressions += Expression().apply {
                        this.variable = variable      // ✅ ТОЛЬКО имя переменной
                        this.type = "_"
                        this.receiver = receiver      // ✅ объект вызова
                        this.method = methodName
                        this.params = params
                    }

                    variable
                }

                is KtDotQualifiedExpression -> {
                    // не трогаем variable, только идём к selector
                    expr.selectorExpression
                        ?.let { processExpression(it, assignedVar) }
                        ?: expr.text
                }

                else -> expr.text
            }

        // --- val / var ---
        fn.collectDescendantsOfType<KtProperty>().forEach { prop ->
            val varName = prop.name ?: generateTmp()
            prop.initializer?.let {
                processExpression(it, varName)
                assignments += "$varName = ${it.text}"
            }
        }

        // --- вызовы без присваивания ---
        fn.collectDescendantsOfType<KtCallExpression>()
            .filter { it.parent !is KtProperty }
            .forEach { processExpression(it) }

        return expressions to assignments
    }
}