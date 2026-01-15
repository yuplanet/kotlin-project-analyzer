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

    var _projectClasses = listOf<KotlinClass>()

    fun collect(projectClasses: List<KotlinClass>) {
        _projectClasses = projectClasses
        try {

            for (cls in projectClasses) {
                for (method in cls.functionCalls) {
                    for (expression in method.fullExpressions) {
                        resolveExpression(expression.target, method)
                        resolveExpression(expression.source, method)
                    }
                }

               // for (property in cls.propertyReferences) {
               //     for (expression in property.fullExpressions) {
               //         resolveExpression(expression.target, property)
               //         resolveExpression(expression.source, property)
               //     }
               // }
               // for (parameter in cls.parameterReferences) {
               //     for (expression in parameter.fullExpressions) {
               //         resolveExpression(expression.target, parameter)
               //         resolveExpression(expression.source, parameter)
               //     }
               // }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun resolveExpression(
        expr: ExpressionValue?,
        caller: ClassMethod
    ) {
        when (expr) {
            is MethodValue -> resolveMethod(expr, caller)
            //is FieldValue -> resolveProperty(expr, caller, callerClass)
            //is VariableValue -> resolveParameter(expr, caller, callerClass)
        }
    }

    private fun resolveMethod(
        value: MethodValue,
        callerMethod: ClassMethod,
    ) {
        var callingMethod = searchEngine.findMethodByClassNameAndMethodNameAndParams(
            className = value.receiverClassName,
            methodName = value.methodName,
            params = value.parameters.map { it.valueType }
        )

        callingMethod = findCalleeMethodByCount(callingMethod, value)
        callingMethod ?: return

        val allMethods = searchEngine.findAllMethodByClassNameAndMethodNameAndParams(
            callingMethod.parentClass.name,
            callingMethod.name,
            callingMethod.parameterTypeNames
        )

        for (method in allMethods) {

            val referenceTargetName =
                method.fullName.replace("${value.receiverClassName}::", "${method.parentClass.name}::")
            val signature = value.methodSignature.replace("${value.receiverClassName}.", "${method.parentClass.name}.")

            value.receiverClassName = method.parentClass.name

            val reference = MethodReference(
                referenceTargetName = referenceTargetName,
                referenceTargetParentClass = method.parentClass,
                method = value,
                signature = signature
            )

            callerMethod.callRecords.add(reference)

            val callers = searchEngine.findCallerMethodsByMethod(callingMethod)

            for (caller in callers) {
                val reverseReference = MethodReference(
                    referenceTargetName = caller.fullName,
                    referenceTargetParentClass = caller.parentClass,
                    method = value,
                    signature = signature
                )

                callingMethod.reverseCallRecords.add(reverseReference)
            }
        }
    }


    fun findCalleeMethodByCount(callee: ClassMethod?, value: MethodValue):  ClassMethod? {

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

                            if (param is VariableValue)
                                param.variableType = realParamType
                            else if (param is FieldValue)
                                param.fieldType = realParamType
                            else if (param is MethodValue)
                                param.methodReturnType = realParamType
                        }

                        // тип известен, но отличается -> не та перегрузка
                        param.valueType.replace("?","") != realParamType.replace("?","") -> {
                            compatible = false
                        }
                    }
                }

                if (compatible) {
                    return candidate
                }
            }
        }
        return  callee
    }
}
