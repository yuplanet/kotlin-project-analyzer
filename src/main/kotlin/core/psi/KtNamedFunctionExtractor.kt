package org.example.core.psi

import org.example.data.symbol.FullExpression
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File


object KtNamedFunctionExtractor {
    private var tmpCounter = 0



    fun collectFullExpressions(fn: KtNamedFunction): List<FullExpression> {
        val result = mutableListOf<FullExpression>()

        fun process(element: PsiElement, currentVar: String = "") {
            when (element) {

                // --- переменные ---
                is KtProperty -> {
                    val varName = element.name ?: ""
                    val type = element.typeReference?.text ?: "_"
                    element.initializer?.let { init ->
                        // если RHS вызов метода
                        if (init is KtCallExpression) {
                            val expr = buildFullExpression(init, varName)
                            result += expr
                        }
                        process(init, varName)
                    }
                }

                // --- вызовы методов ---
                is KtCallExpression -> {
                    val expr = buildFullExpression(element, currentVar)
                    result += expr
                    // рекурсивно по аргументам
                    element.valueArguments.forEach { it.getArgumentExpression()?.let { process(it) } }
                }

                // --- dot цепочки ---
                is KtDotQualifiedExpression -> element.selectorExpression?.let { process(it) }

                // --- safe call цепочки ---
                is KtSafeQualifiedExpression -> element.selectorExpression?.let { process(it) }

                // --- блоки ---
                is KtBlockExpression -> element.statements.forEach { process(it) }

                // --- циклы ---
                is KtForExpression -> element.body?.let { process(it) }
                is KtWhileExpression -> element.body?.let { process(it) }

                // --- if ---
                is KtIfExpression -> {
                    element.then?.let { process(it) }
                    element.`else`?.let { process(it) }
                }

                // --- try/catch/finally ---
                is KtTryExpression -> {
                    process(element.tryBlock)
                    element.catchClauses.forEach { clause -> clause.catchBody?.let { process(it) } }
                    element.finallyBlock?.finalExpression?.let { process(it) }
                }

                else -> element.children.forEach { process(it) }
            }
        }

        fn.bodyExpression?.let { process(it) }
        return result
    }

    // --- helper для построения FullExpression ---
    fun buildFullExpression(call: KtCallExpression, variable: String = ""): FullExpression {
        val method = call.calleeExpression?.text ?: ""
        val receiver = (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text
            ?: (call.parent as? KtSafeQualifiedExpression)?.receiverExpression?.text
            ?: "this"
        val params = call.valueArguments.map { it.getArgumentExpression()?.text ?: "?" }

        return FullExpression(
            variable = variable,
            receiver = receiver,
            method = method,
            params = params
        )
    }



    fun parseFunctionContent(
        fn: KtNamedFunction
    ): Pair<List<FullExpression>, List<String>> {
        val wfun = collectFullExpressions(fn)

        val dotCalls3 =
            fn.bodyBlockExpression
                ?.collectDescendantsOfType<KtDotQualifiedExpression>()
                ?.filter { it.selectorExpression is KtCallExpression }
                ?: emptyList()



        val dotCallsString3 = dotCalls3.map { expr ->
            val receiver = expr.receiverExpression.text
            val call = expr.selectorExpression as KtCallExpression
            val method = call.calleeExpression?.text ?: "unknown"
            val args = call.valueArguments.joinToString(",") { it.text }
            "$receiver.$method($args)"
        }

        data class StatementDump(
            val type: String,
            val text: String
        )

        val statementsDump = mutableListOf<StatementDump>()

        fn.bodyBlockExpression
            ?.statements
            ?.forEach { stmt ->
                statementsDump += StatementDump(
                    type = stmt::class.simpleName ?: "Unknown",
                    text = stmt.text
                )
            }

        // Вывод в консоль

        val expressions = mutableListOf<FullExpression>()
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

                    expressions += FullExpression().apply {
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