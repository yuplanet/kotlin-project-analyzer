package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodReference
import org.example.data.symbol.expression.ExpressionValue
import org.example.data.symbol.expression.MethodValue

class ExpressionCallResolver( private val searchEngine: IProjectSearchEngine) {

    fun collect(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            for (method in cls.functionCalls) {

                for (expression in method.fullExpressions) {
                    resolveExpression(expression.target, method, cls)
                    resolveExpression(expression.source, method, cls)
                }
            }
        }
    }

    private fun resolveExpression(
        expr: ExpressionValue?,
        caller: ClassMethod,
        callerClass: KotlinClass
    ) {
        when (expr) {
            is MethodValue -> resolveMethod(expr, caller, callerClass)
            //is FieldValue -> resolveProperty(expr, caller, callerClass)
            //is VariableValue -> resolveParameter(expr, caller, callerClass)
        }
    }

    private fun resolveMethod(
        value: MethodValue,
        caller: ClassMethod,
        callerClass: KotlinClass
    ) {
        var callee = searchEngine.findMethodByClassNameAndMethodNameAndParams(
            className = value.receiverClassName,
            methodName = value.methodName,
            params = value.parameters.map { it.valueType }
        )

        //if (callee == null && value.parameters.any { it.valueType == "unknown" }) {
        //    callee = searchEngine.findMethodByClassNameAndMethodNameAndParamsCount(
        //        className = value.receiverClassName,
        //        methodName = value.methodName,
        //        paramsCount = value.parameters.count()
        //    )
        //    if(callee != null)
        //        print(1)
        //}
        callee ?: return

        val parentClass =
            searchEngine.findByClassName(value.receiverClassName) ?: return

        val reference = MethodReference(
            referenceTargetName = callee.fullName,
            referenceTargetParentClass = parentClass,
            method = value,
            signature = value.methodSignature
        )

        caller.callRecords.add(reference)

        val reverseReference = MethodReference(
            referenceTargetName = caller.fullName,
            referenceTargetParentClass = callerClass,
            method = value,
            signature = ""
        )

        callee.reverseCallRecords.add(reverseReference)
    }
}
