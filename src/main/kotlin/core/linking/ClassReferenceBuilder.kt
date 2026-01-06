package org.example.core.linking

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.psi.KtExpressionChainBuilder
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodReference
import org.example.data.symbol.expression.ExpressionValue
import org.example.data.symbol.expression.MethodValue

class ClassReferenceBuilder (): IClassReferenceBuilder {

    private lateinit var searchEngine: IProjectSearchEngine

    override fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine) {

        this.searchEngine = searchEngine

        // 11 collect function expressions
        collectExpressions(projectClasses)
        collectCalls(projectClasses)

    }

    fun collectExpressions(projectClasses: List<KotlinClass>) {

        for (cls in projectClasses) {

            for (method in cls.functionCalls) {
               //if (method.name != "sendScheduledEnvelopeNotification")
               //    continue

                val expressionCollector = KtExpressionChainBuilder(method, cls, searchEngine);
                expressionCollector.collectTopLevelExpressions()
                println()
            }
        }
    }


    fun collectCalls(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            for (method in cls.functionCalls) {
                buildCallRecordsForMethod(method)
            }
        }
    }


    private fun buildCallRecordsForMethod(
        method: ClassMethod
    ) {
        for (expression in method.fullExpressions) {
            resolveMethodCall(expression.target, method)
            resolveMethodCall(expression.source, method)
        }
    }

    private fun resolveMethodCall(
        expr: ExpressionValue?,
        caller: ClassMethod
    ) {
        val methodValue = expr as? MethodValue ?: return

        // resolve real method from project
        val callee = searchEngine.findMethodByClassNameAndMethodNameAndParams(
            className = methodValue.receiverClassName,
            methodName = methodValue.methodName,
            params = methodValue.parameters.map { "it.variableType" }
        ) ?: return

        val parentClass = searchEngine.findByClassName(methodValue.receiverClassName)

        val reference = MethodReference(
            name = callee.fullName,
            parentClass = parentClass!!,
            method = methodValue
        )

        // direct call
        caller.callRecords.add(reference)

        // reverse call
        callee.reverseCallRecords.add(reference)
    }
}