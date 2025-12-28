package org.example.core.linking

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.psi.KtExpressionChainBuilder
import org.example.data.symbol.*
import java.io.File

class ClassReferenceBuilder (): IClassReferenceBuilder {

    private lateinit var searchEngine: IProjectSearchEngine

    override fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine) {

        this.searchEngine = searchEngine


        // 11 collect function expressions
        collectExpressions2(projectClasses)
    }

    fun collectExpressions(projectClasses: List<KotlinClass>)  {
        val logDirectory = File("logs")
        if (!logDirectory.exists()) logDirectory.mkdirs()  // создаём папку logs

        for (cls in projectClasses) {
            val logFile = File(logDirectory, "${cls.name}_expressions.log")

            logFile.printWriter().use { out ->
                out.println("Class: ${cls.name}")
                out.println("Path: ${cls.path}")
                out.println("Methods expressions:")

                for (method in cls.functionCalls) {
                    out.println("\nMethod: ${method.name}")

                    // Собираем expressions для метода
                    val expressionCollector = KtExpressionChainBuilder(method, cls, searchEngine)
                    expressionCollector.collectExpressions()

                        // for (expr in method.fullExpressions) {
                   //     when (expr) {
                   //         is VariableFromMethodExpression -> {
                   //             out.println("VariableAssignment -> target: ${expr.target?.name}, receiver: ${expr.receiver?.name}, method: ${expr.method?.name}")
                                        //         }
                   //         is FieldFromVariableExpression -> {
                   //             out.println("FieldAssignment -> target: ${expr.target?.name}, source: ${expr.source?.name}")
                                        //         }
                   //         is VariableFromFieldExpression -> {
                   //             out.println("VariableToField -> target: ${expr.target?.name}, source: ${expr.source?.name}")
                                        //         }
                   //         is FieldFromFieldExpression -> {
                   //             out.println("FieldToField -> target: ${expr.target?.name}, source: ${expr.source?.name}")
                                        //         }
                   //         else -> {
                   //             out.println("Unknown expression: $expr")
                                        //         }
                   //     }
                                // }
                }
            }
        }
    }

    fun collectExpressions2(projectClasses: List<KotlinClass>) {

        for (cls in projectClasses) {

            for (method in cls.functionCalls) {
                if (method.name != "sendScheduledEnvelopeNotification")
                    continue

                val expressionCollector = KtExpressionChainBuilder(method, cls, searchEngine);
                expressionCollector.collectExpressions()

                println(1)
            }
        }
    }
}