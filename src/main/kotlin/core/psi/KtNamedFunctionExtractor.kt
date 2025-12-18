package org.example.core.psi

import org.example.data.symbol.FullExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File


object KtNamedFunctionExtractor {
    private var tmpCounter = 0
    fun collectAllMethodCalls(fn: KtNamedFunction): List<String> {
        val calls = mutableListOf<String>()

        // Рекурсивно находим receiver
        fun findReceiver(call: KtCallExpression): String {
            var expr: KtExpression = call
            val receivers = mutableListOf<String>()
            while (true) {
                val parent = expr.parent
                when (parent) {
                    is KtDotQualifiedExpression -> {
                        receivers.add(parent.receiverExpression.text)
                        expr = parent
                    }
                    is KtSafeQualifiedExpression -> {
                        receivers.add(parent.receiverExpression.text + "?")
                        expr = parent
                    }
                    else -> break
                }
            }
            return if (receivers.isEmpty()) "this" else receivers.reversed().joinToString(".")
        }

        fun buildFullCall(expr: KtExpression): String? {
            return when (expr) {
                is KtCallExpression -> {
                    val receiver = findReceiver(expr)
                    val methodName = expr.calleeExpression?.text ?: "unknown"
                    val args = expr.valueArguments.map { it.getArgumentExpression()?.text ?: "?" }
                    "$receiver.$methodName(${args.joinToString(", ")})"
                }
                is KtDotQualifiedExpression -> {
                    expr.selectorExpression?.let { buildFullCall(it) }
                }
                is KtSafeQualifiedExpression -> {
                    expr.selectorExpression?.let { buildFullCall(it)?.let { s -> "${expr.receiverExpression.text}?." + s.substringAfter('.') } }
                }
                else -> null
            }
        }

        fun process(expr: KtExpression) {
            when (expr) {

                // Переменные
                is KtProperty -> {
                    val varName = expr.name ?: "__no_name__"
                    expr.initializer?.let { init ->
                        val callText = buildFullCall(init)
                        if (callText != null) calls += "$varName = $callText"
                        process(init) // рекурсивно обрабатываем выражение
                    }
                }

                // Вызовы
                is KtCallExpression -> {
                    buildFullCall(expr)?.let { calls += it }
                    expr.valueArguments.forEach { arg -> arg.getArgumentExpression()?.let { process(it) } }
                }

                // DotQualified и SafeQualified рекурсивно
                is KtDotQualifiedExpression -> expr.selectorExpression?.let { process(it) }
                is KtSafeQualifiedExpression -> expr.selectorExpression?.let { process(it) }

                // Блоки и управляющие конструкции
                is KtBlockExpression -> expr.statements.forEach { process(it) }
                is KtForExpression -> expr.body?.let { process(it) }
                is KtWhileExpression -> expr.body?.let { process(it) }
                is KtIfExpression -> { expr.then?.let { process(it) }; expr.`else`?.let { process(it) } }
                is KtTryExpression -> {
                    process(expr.tryBlock)
                    expr.catchClauses.forEach { it.catchBody?.let { b -> process(b) } }
                    expr.finallyBlock?.finalExpression?.let { process(it) }
                }
            }
        }

        // Рекурсивно строим полный вызов с receiver




        fn.bodyBlockExpression?.statements?.forEach { process(it) }
        return calls
    }


    fun parseFunctionContent(
        fn: KtNamedFunction
    ): Pair<List<FullExpression>, List<String>> {
        collectAllMethodCalls(fn)

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