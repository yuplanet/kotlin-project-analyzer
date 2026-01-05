package org.example.core.psi

import org.example.core.interfaces.IExpressionTypeResolver
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.interfaces.ITemporaryVariableStorage
import org.example.core.linking.ExpressionTypeResolver
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.enum.AssigmentExpressionType
import org.example.data.symbol.expression.*
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.slf4j.LoggerFactory


class KtExpressionChainBuilder(

    private val currentMethod: ClassMethod,
    private val mainClass: KotlinClass,
    private val searchEngine: IProjectSearchEngine){

    private var typeResolver: IExpressionTypeResolver = ExpressionTypeResolver(searchEngine, mainClass, currentMethod)
    private val variableStorage: ITemporaryVariableStorage = ExpressionValueStorage()

    private val log = LoggerFactory.getLogger(KtExpressionChainBuilder::class.java)

    val unknownType = "unknown"

    fun collectTopLevelExpressions() {
        val block = currentMethod.function.bodyBlockExpression ?: return

        for (statement in block.statements) {
            handleTopLevelExpression(statement)
        }
    }


    fun handleTopLevelExpression(expr: KtExpression) {
        when (expr) {

            is KtIfExpression -> {
                expr.then?.let { handleTopLevelExpression(it) }
                expr.`else`?.let { handleTopLevelExpression(it) }
            }

            is KtWhenExpression -> {
                expr.entries.forEach { entry ->
                    entry.expression?.let { handleTopLevelExpression(it) }
                }
            }

            is KtForExpression -> {
                expr.body?.let { handleTopLevelExpression(it) }
            }

            is KtWhileExpression -> {
                expr.body?.let { handleTopLevelExpression(it) }
            }

            is KtDoWhileExpression -> {
                expr.body?.let { handleTopLevelExpression(it) }
            }

            is KtTryExpression -> {
                // try
                handleTopLevelExpression(expr.tryBlock)

                // catch
                expr.catchClauses.forEach { clause ->
                    clause.catchBody?.let { body ->
                        handleTopLevelExpression(body)
                    }
                }

                // finally
                expr.finallyBlock?.finalExpression?.let {
                    handleTopLevelExpression(it)
                }
            }

            is KtIsExpression -> {
                expr.leftHandSide?.let {
                    handleTopLevelExpression(it)
                }
            }

            is KtLambdaExpression -> {
                expr.bodyExpression?.let {
                    handleTopLevelExpression(it)
                }
            }

            is KtArrayAccessExpression -> {
                expr.arrayExpression?.let {
                    handleTopLevelExpression(it)
                }
                expr.indexExpressions.forEach {
                    handleTopLevelExpression(it)
                }
            }

            is KtBlockExpression -> {
                expr.statements.forEach {
                    handleTopLevelExpression(it)
                }
            }

            else -> {
                handlePsiElement(expr)
            }
        }
    }

    // основные элементы на которые
    fun handlePsiElement(
        element: PsiElement,
        context: ExpressionValue? = null,
        recursionDepth: Int = 1
    ): ExpressionValue? {

        logInfo(element.text, recursionDepth)

        val target: ExpressionValue? = when (element) {

            is KtNameReferenceExpression -> {
                var value = getVariableOrFieldValueFromExpression(element)
                return value
            }

            is KtProperty -> {

                val target = getVariableOrFieldValueFromExpression(element)

                val initializer = element.initializer
                var source: ExpressionValue? = null

                initializer?.let {
                    logInfo(element.text, recursionDepth + 1)
                    source = handlePsiElement(it)
                }

                if (target is VariableValue) {
                    val property = ClassProperty(target.variableName, target.variableType, element)
                    currentMethod.properties.add(property)
                }

                addExpression(target, source)
                return target
            }

            is KtParameter -> {
                val target = getVariableOrFieldValueFromExpression(element)

                if (target is VariableValue) {
                    val parameter = ClassParameter(target.variableName, target.variableType, element)
                    currentMethod.parameters.add(parameter)
                }
                return target
            }

            is KtCallExpression -> {
                element.text
                for (arg in element.valueArguments) {

                    val expression = arg.getArgumentExpression()
                    arg.text

                    expression?.let { handlePsiElement(it, null) }
                }

                var receiver = context?.let {  getReceiveVariable(it)}

                val method =  completeMethod(element, receiver)

                val tmpName = variableStorage.add(method)

                val target = VariableValue(tmpName, method.methodReturnType)

                addExpression(target, method)

                // 5️⃣ возвращаем результат вызова
                target
            }

            is KtDotQualifiedExpression,
            is KtSafeQualifiedExpression, -> {

                val dotExpression = element as KtQualifiedExpression

                val leftExpression: KtExpression = dotExpression.receiverExpression
                leftExpression.text

                val rightExpression = dotExpression.selectorExpression
                rightExpression?.text

                /// left . right
                var target: ExpressionValue? = handlePsiElement(leftExpression)

                if(target is VariableValue && target.variableType == unknownType && rightExpression?.text!=null) {
                    val type = typeResolver.getVariableTypeByNameAndClass(leftExpression.text, rightExpression.text)
                    target =  VariableValue(rightExpression.text, type?:unknownType)
                }

                val source: ExpressionValue? = rightExpression?.let {
                    handlePsiElement(it, target)
                }

                addExpression(target, source)

                target
            }

            is KtBinaryExpression -> {
                if (element.operationToken != KtTokens.EQ)
                    return null

                val leftExpression = element.left ?: error("Left-hand side missing ${element.left?.text}")
                val rightExpression = element.right ?: error("Right-hand side missing ${element.right?.text}")

                // 1️⃣ обрабатываем правую часть (источник)
                val source = handlePsiElement(rightExpression, null)

                // 2️⃣ обрабатываем левую часть (цель)
                val target = handlePsiElement(leftExpression, null)

                addExpression(target, source)

                target
            }

            is KtUnaryExpression -> {
                null
            }

            is KtReturnExpression -> {
                null
            }

            is KtThrowExpression -> {
                null
            }

            is KtReturnExpression -> {
                null
            }

            else -> {
                null
            }
        }

        return target
    }




    /////////////////////////////// Process chapter
    fun completeMethod(expression: KtCallExpression, receiver: VariableValue?): MethodValue {

        //Metho
        var methodName = expression.calleeExpression?.text ?: "" // мя метода

        val params = expression.valueArguments.mapNotNull { arg ->

            val argExpr = arg.getArgumentExpression()
            val rawText = argExpr?.text ?: ""

            // Определяем имя параметра с подстановкой tmpMap для сложных выражений
            var paramName = when (argExpr) {
                is KtCallExpression -> {
                    val innerReceiver = (argExpr.parent as? KtDotQualifiedExpression)?.receiverExpression
                    val innerText = innerReceiver?.text

                    if (innerText != null) {
                        ""//variableStorage.getByRawValue(innerText)?.second ?: rawText
                    } else rawText
                }

                else -> {
                    ""//variableStorage.getLastByValue(rawText)?.second ?: rawText
                }
            }


            if (paramName.isBlank()) return@mapNotNull null

            val paramType = typeResolver.getVariableTypeByName(paramName) ?: unknownType

            VariableValue(paramName, paramType)
        }

        var method = MethodValue(

            methodName = methodName,
            parameters = params.toMutableList(),
            rawValue = KtExpressionInspector.getRawCallText(expression),
            methodReturnType = unknownType
        )

        if (receiver != null) {

            //
            method.receiverName = receiver.variableName
            method.receiverClassName = receiver.variableType
            method.innerCall = false
        } else {
            method.receiverName = "this."
            method.receiverClassName = mainClass.name
            method.innerCall = true
        }

        val methodType = typeResolver.getMethodReturnTypeByNameReceiveAndParamTypes(
            methodName = methodName,
            receiverClass = receiver?.variableType?:unknownType,
            params.map { it.variableType }
        ) ?: unknownType

        method.methodReturnType = methodType
        return method
    }

    /////// Value Chapter

    //принимает xpressionValue И дает receive в виде Variable
    fun getReceiveVariable(expr: ExpressionValue): VariableValue? {

        if (expr is VariableValue) {
            return VariableValue(expr.variableName, expr.variableType)
        } else if (expr is FieldValue) {
            return VariableValue(expr.fieldName, expr.fieldType)
        } else if (expr is MethodValue) {
            return VariableValue(expr.methodName, expr.methodReturnType)
        }
        return null
    }

    fun getVariableOrFieldValueFromExpression(expr: KtExpression): ExpressionValue? {

        var variable: ExpressionValue? = null

        if (expr is KtNameReferenceExpression)
            variable = getVariableValueFromExpression(expr)
        else if (expr is KtDotQualifiedExpression || expr is KtSafeQualifiedExpression)
            variable = getFieldValueFromExpression(expr)

        return variable
    }


    private fun getVariableValueFromExpression(variableExpr: KtNameReferenceExpression): VariableValue {
        val name = variableExpr.getReferencedName()
        return getVariableValueFromName(name)
    }

    private fun getVariableValueFromName(variableName: String): VariableValue {
        val type = typeResolver.getVariableTypeByName(variableName) ?: unknownType
        return VariableValue(variableName, type)
    }


    private fun getFieldValueFromExpression(expression: KtExpression): ExpressionValue? {
        val fieldExpression = when (expression) {
            is KtDotQualifiedExpression -> expression
            is KtSafeQualifiedExpression -> expression
            else -> return null
        }

        val className = fieldExpression.receiverExpression.text
        val fieldName = fieldExpression.selectorExpression?.text ?: unknownType


        //receiver
        val classType = typeResolver.getVariableTypeByName(className) ?: unknownType

        var fieldType = typeResolver.getVariableTypeByNameAndClass(classType, fieldName)

        val target = FieldValue(
            fieldName = fieldName,
            fieldType = fieldType?:unknownType,
            qualifier = className,
            qualifierType = classType
        )

        return target
    }



    ///////// others
    private fun addExpression(target: ExpressionValue?, source: ExpressionValue?, operationType: AssigmentExpressionType = AssigmentExpressionType.Undefined) {

        var operation =
            AssignmentExpression(
                target = target,
                source = source,
                operationType = operationType,
            )

        currentMethod.fullExpressions.add(operation)
    }

    private fun logInfo( message: String, level: Int = 1) {
        val indent = "  ".repeat(level)
        log.info("{}{}", indent, message)
    }
}