package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodReference
import org.example.data.symbol.expression.ExpressionValue
import org.example.data.symbol.expression.FieldValue
import org.example.data.symbol.expression.MethodValue
import org.example.data.symbol.expression.VariableValue

class ExpressionCallResolver( private val searchEngine: IProjectSearchEngine) {

    fun collect(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            for (method in cls.functionCalls) {
                for (expression in method.fullExpressions) {
                    resolveExpression(expression.target, method, cls)
                    resolveExpression(expression.source, method, cls)
                }

                if(method.name == "sendToNextRecipient")
                    print(1)
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

        if (callee == null && value.parameters.any { it.valueType == "unknown" }) {
            var candidate = searchEngine.findMethodByClassNameAndMethodNameAndParamsCount(
                className = value.receiverClassName,
                methodName = value.methodName,
                paramsCount = value.parameters.count()
            )
            if (candidate != null) {

                // сверяем и обновляем параметры по индексу
                var compatible = true

                value.parameters.forEachIndexed { index, param ->

                    val realParamType = candidate.parameters.getOrNull(index)?.type ?: return@forEachIndexed

                    when {
                        // unknown -> просто обновляем тип
                        param.valueType == "unknown" -> {

                            if(param is VariableValue)
                                param.variableType = realParamType
                            else if(param is FieldValue)
                                param.fieldType = realParamType
                            else if(param is MethodValue)
                                param.methodReturnType = realParamType
                        }

                        // тип известен, но отличается -> не та перегрузка
                        param.valueType != realParamType -> {
                            compatible = false
                        }
                    }
                }

                if (compatible) {
                    callee = candidate
                }
            }
        }
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
