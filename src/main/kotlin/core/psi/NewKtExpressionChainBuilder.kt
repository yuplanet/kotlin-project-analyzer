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


class NewKtExpressionChainBuilder(
    private val currentMethod: ClassMethod,
    private val mainClass: KotlinClass,
    private val searchEngine: IProjectSearchEngine){

    private var typeResolver: IExpressionTypeResolver = ExpressionTypeResolver(searchEngine, mainClass, currentMethod)
    private val variableStorage: ITemporaryVariableStorage = TemporaryVariableStorage()

    private val log = LoggerFactory.getLogger(NewKtExpressionChainBuilder::class.java)

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
                getVariableOrField(element)
            }

            is KtProperty -> {

                val varName = element.name ?: unknownType
                var varType = element.typeReference?.text ?: typeResolver.getVariableType(varName) ?: unknownType

                val initializer = element.initializer

                var source: ExpressionValue? = null


                initializer?.let {

                    it.text
                    source = handlePsiElement(it)
                }

                var operationType = AssigmentExpressionType.Undefined

                if (varType == unknownType) {

                    varType = when (source) {
                        is MethodValue -> {
                            operationType = AssigmentExpressionType.VariableFromMethod
                            source.methodReturnType
                        }

                        is FieldValue -> {
                            operationType = AssigmentExpressionType.VariableFromField
                            source.fieldType
                        }

                        is VariableValue -> {
                            operationType = AssigmentExpressionType.VariableFromVariable
                            source.variableType
                        }

                        else -> unknownType
                    }
                }

                val property = ClassProperty(varName, varType, element)
                currentMethod.properties.add(property)

                var target = VariableValue(variableName = varName, variableType = varType)

                addExpression(target, source, operationType)
                target
            }

            is KtParameter -> {
                val varName = element.name ?: unknownType
                val varType = element.typeReference?.text ?: typeResolver.getVariableType(varName) ?: unknownType

                val parameter = ClassParameter(varName, varType, element)
                currentMethod.parameters.add(parameter)
                null
            }

            is KtCallExpression -> {

                for (arg in element.valueArguments) {

                    val expression = arg.getArgumentExpression()

                    if (expression != null)
                        handlePsiElement(expression, null)
                }

                val method = if (context is MethodValue) {
                    context
                } else {
                    MethodValue(innerCall = true)
                }

                processMethod(element, method)

                val (tmpName, _) = variableStorage.add(method.rawContent)

                val target = VariableValue(tmpName, method.methodReturnType)

                addExpression(target, method)

                // 5️⃣ возвращаем результат вызова
                target
            }

            is KtDotQualifiedExpression,
            is KtSafeQualifiedExpression -> {

                val dotExpression = element as KtQualifiedExpression

                val leftExpression: KtExpression = dotExpression.receiverExpression
                leftExpression.text

                val rightExpression = dotExpression.selectorExpression
                rightExpression?.text

                /// left . right

                var leftVariable: ExpressionValue? =
                    processSelectorExpression(leftExpression)

                if(leftVariable == null){
                    leftVariable = getVariableValue(leftExpression.text)
                }

                val rightVariable: ExpressionValue? = rightExpression?.let {
                    processSelectorExpression(it, leftVariable)
                }

                addExpression(leftVariable, rightVariable)

                leftVariable
            }

            is KtBinaryExpression -> {
                if (element.operationToken != KtTokens.EQ)
                    return null

                val leftExpression = element.left ?: error("Left-hand side missing")
                val rightExpression = element.right ?: error("Right-hand side missing")

                leftExpression.text
                rightExpression.text

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




    ///////////////////////////////methiods
    private fun isNestedExpression(expr: KtExpression?): Boolean {

        if (expr == null) return false

        return when (expr) {
            is KtBinaryExpression -> {
                // Если левая или правая часть — сложная
                isNestedExpression(expr.left) || isNestedExpression(expr.right)
            }

            is KtCallExpression -> {
                // Если есть receiver или аргументы с вызовами
                val receiver = (expr.parent as? KtDotQualifiedExpression)?.receiverExpression
                receiver is KtCallExpression || receiver is KtDotQualifiedExpression || expr.valueArguments.any {
                    isNestedExpression(it.getArgumentExpression())
                }
            }

            is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {
                val receiver = (expr as KtQualifiedExpression).receiverExpression
                receiver is KtCallExpression || receiver is KtDotQualifiedExpression || isNestedExpression(receiver)
            }

            else -> false
        }
    }

    private fun processSelectorExpression(
        currentExpression: KtExpression,
        receiverVar: ExpressionValue? = null,
    ): ExpressionValue? {

        if (currentExpression is KtCallExpression) {

            val methodInfo = if (receiverVar != null && receiverVar is VariableValue) {

                val variable = receiverVar as VariableValue
                MethodValue(
                    receiverName = variable.variableName,
                    receiverClassName = variable.variableType
                )
            } else {
                MethodValue(
                    receiverName = "this.",
                    receiverClassName = mainClass.name,
                    innerCall = true
                )
            }

            // parent = parent (тот, кто ждёт результат), receiver = receiverVar (через кого идёт вызов)
            return handlePsiElement(currentExpression, methodInfo)

        } else if (currentExpression is KtDotQualifiedExpression || currentExpression is KtSafeQualifiedExpression) {

            val innerReceiver = getVariableOrField(currentExpression.receiverExpression) ?: VariableValue()

            return if (isNestedExpression(currentExpression)) {

                val (tmpName, _) = variableStorage.add("innerReceiver.name")
                val tmpVar = VariableValue(tmpName, unknownType)

                val expr = AssignmentExpression(
                    target = tmpVar,
                    source = innerReceiver,
                )
                currentMethod.fullExpressions.add(expr)

                currentExpression.selectorExpression?.let {
                    handlePsiElement(it, tmpVar)
                }
                tmpVar
            } else {
                innerReceiver
            }
        }
        return receiverVar
    }

    /////Helpers

    fun processMethod(expression: KtCallExpression, method: MethodValue?) {

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
                        variableStorage.getLastByValue(innerText)?.second ?: rawText
                    } else rawText
                }

                else -> {
                    variableStorage.getLastByValue(rawText)?.second ?: rawText
                }
            }


            if (paramName.isBlank()) return@mapNotNull null

            val paramType = typeResolver.getVariableType(paramName) ?: unknownType

            VariableValue(paramName, paramType)
        }

        val processedMethod = if (method != null) {
            method.methodName = methodName
            method.parameters = params.toMutableList()
            method.rawContent = getRawCallText(expression)

            method
        } else {
            MethodValue(
                methodName = methodName,
                methodReturnType = unknownType,
                parameters = params.toMutableList(),
                rawContent = getRawCallText(expression)
            )
        }


        val methodType = typeResolver.getMethodReturnType(processedMethod) ?: unknownType
        processedMethod.methodReturnType = methodType
    }

    private fun getRawCallText(call: KtCallExpression): String {
        var element: PsiElement = call

        while (true) {
            val parent = element.parent

            when (parent) {

                is KtDotQualifiedExpression -> {
                    // Поднимаемся ТОЛЬКО если текущий элемент — receiver
                    if (parent.receiverExpression == element)
                        element = parent
                    else
                        break
                }

                is KtSafeQualifiedExpression -> {
                    if (parent.receiverExpression == element)
                        element = parent
                    else
                        break
                }

                else -> break
            }
        }
        return element.text
    }

    fun getVariableOrField(expr: KtExpression): ExpressionValue? {
        var variable: ExpressionValue? = null

        if (expr is KtNameReferenceExpression) {
            variable = getVariableInfoFromKNamedExpression(expr)
        } else if (expr is KtDotQualifiedExpression || expr is KtSafeQualifiedExpression) {
            variable = getFieldInfoFromDotQualified(expr)
        }

        return variable
    }

    private fun addExpression(target: ExpressionValue?, source: ExpressionValue?, operationType: AssigmentExpressionType = AssigmentExpressionType.Undefined) {

        var operation =
            AssignmentExpression(
                target = target,
                source = source,
                operationType = operationType,
            )

        currentMethod.fullExpressions.add(operation)
    }

    private fun getVariableInfoFromKNamedExpression(variableExpr: KtNameReferenceExpression): VariableValue {
        val name = variableExpr.getReferencedName()

        val type = typeResolver.getVariableType(name) ?: unknownType

        return VariableValue(name, type)
    }

    private fun getVariableValue(variableName: String): VariableValue {

        val type = typeResolver.getVariableType(variableName) ?: unknownType


        return VariableValue(variableName, type)
    }


    private fun getFieldInfoFromDotQualified(expression: KtExpression): ExpressionValue? {
        val fieldExpression = when (expression) {
            is KtDotQualifiedExpression -> expression
            is KtSafeQualifiedExpression -> expression
            else -> null
        } ?: return null

        val receiverName = fieldExpression.receiverExpression.text
        val fieldName = fieldExpression.selectorExpression?.text ?: unknownType


        //receiver
        val receiverClass =  typeResolver.getVariableType(receiverName)?:unknownType

        var fieldType = typeResolver.getFieldTypeByClass(receiverName, fieldName)

        if(fieldType == null)
                fieldType = typeResolver.getFieldTypeByClass(receiverClass, fieldName)


        val receiver = VariableValue(receiverName, receiverClass)

        return FieldValue(
            fieldName = fieldName,
            fieldType = fieldType?:unknownType
        )
    }



    private fun logInfo( message: String, level: Int = 1) {
        val indent = "  ".repeat(level)
        log.info("{}{}", indent, message)
    }
}