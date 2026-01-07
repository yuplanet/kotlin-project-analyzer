package org.example.core.linking

import org.example.core.LogManager
import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodReference
import org.example.data.symbol.expression.ExpressionValue
import org.example.data.symbol.expression.MethodValue
import java.io.File

class ClassReferenceBuilder (): IClassReferenceBuilder {

    private lateinit var searchEngine: IProjectSearchEngine

    private val logFolder = "logs/reference/"
    override fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine, branchName: String) {

        this.searchEngine = searchEngine

        // 11 collect function expressions
        collectExpressions(projectClasses)

        LogManager.logClassAllMethodExpression(projectClasses, logFolder + branchName)


        collectCalls(projectClasses)

        dumpCallGraph(projectClasses)
    }

    fun collectExpressions(projectClasses: List<KotlinClass>) {

        for (cls in projectClasses) {
            for (method in cls.functionCalls) {
                val expressionCollector = ExpressionChainBuilder(method, cls, searchEngine);
                expressionCollector.collectTopLevelExpressions()
            }
        }
    }


    fun collectCalls(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            for (method in cls.functionCalls) {
                for (expression in method.fullExpressions) {
                    resolveMethodCall(expression.target, method, cls)
                    resolveMethodCall(expression.source, method, cls)
                }
            }
        }
    }

    private fun resolveMethodCall(
        expr: ExpressionValue?,
        caller: ClassMethod,
        cls: KotlinClass
    ) {
        val methodValue = expr as? MethodValue ?: return

        // resolve real method from project
        val callee = searchEngine.findMethodByClassNameAndMethodNameAndParams(
            className = methodValue.receiverClassName,
            methodName = methodValue.methodName,
            params = methodValue.parameters.map {  it.valueType }
        )
            ?: return

        val parentClass = searchEngine.findByClassName(methodValue.receiverClassName)

        val reference = MethodReference(
            name = callee.fullName,
            parentClass = parentClass!!,
            method = methodValue,
            signature = ""
        )

        // direct call
        caller.callRecords.add(reference)

        val reverseReference = MethodReference(
            name = caller.fullName,
            parentClass = cls,
            signature = "",
            method = MethodValue(
            )
        )

        // reverse call
        callee.reverseCallRecords.add(reverseReference)
    }

    fun dumpCallGraph(projectClasses: List<KotlinClass>) {
        resetLogDirectory("logs/class_chains")
        val logDir = File("logs/class_chains")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        for (cls in projectClasses) {
            val fileName = cls.name + ".txt"
            val file = File(logDir, fileName)

            val builder = StringBuilder()

            builder.appendLine("Class: ${cls.fullName}")
            builder.appendLine()

            for (method in cls.functionCalls) {

                builder.appendLine("Method: ${method.fullName}")

                // 🔹 direct calls
                if (method.callRecords.isNotEmpty()) {
                    builder.appendLine("  calls:")
                    for (call in method.callRecords) {
                        builder.appendLine("    -> ${call.name}")
                    }
                }

                // 🔹 reverse calls
                if (method.reverseCallRecords.isNotEmpty()) {
                    builder.appendLine("  called by:")
                    for (call in method.reverseCallRecords) {
                        builder.appendLine("    <- ${call.name}")
                    }
                }

                builder.appendLine()
            }

            file.writeText(builder.toString())
        }
    }



    private fun resetLogDirectory(path: String) {
        val logDir = File(path)

        // Если папка существует, удаляем её вместе со всем содержимым
        if (logDir.exists()) {
            logDir.deleteRecursively()
        }

        // Создаём пустую директорию заново
        logDir.mkdirs()
    }
}