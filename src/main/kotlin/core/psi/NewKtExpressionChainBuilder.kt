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
import org.example.data.symbol.expression.AssignmentExpression
import org.example.data.symbol.expression.FieldInfo
import org.example.data.symbol.expression.MethodInfo
import org.example.data.symbol.expression.VariableInfo
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtCallExpression


class NewKtExpressionChainBuilder(
    private val currentMethod: ClassMethod,
    private val mainClass: KotlinClass,
    private val searchEngine: IProjectSearchEngine,
    ) {

    private var typeResolver: IExpressionTypeResolver = ExpressionTypeResolver(searchEngine, mainClass, currentMethod)
    private val variableStorage: ITemporaryVariableStorage = TemporaryVariableStorage()

    val unknownType = "unknown"

    fun collectTopLevelExpressions(function: KtNamedFunction) {
        val block = function.bodyBlockExpression ?: return

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
                handleSysTopLevelExpression(expr)
            }
        }
    }


    // основные элементы на которые
    fun handleSysTopLevelExpression(expr: KtExpression) {
        when (expr) {
            is KtNameReferenceExpression -> {
                processPsiElement(expr)
            }

            is KtProperty -> {
                val varName = expr.name ?: unknownType
                val varType = expr.typeReference?.text ?: typeResolver.getVariableType(varName)?: unknownType

                val property = ClassProperty(varName, varType, expr)
                currentMethod.properties.add(property)
            }
            is KtParameter -> {
                val varName = expr.name ?: unknownType
                val varType = expr.typeReference?.text ?: typeResolver.getVariableType(varName)?: unknownType

                val parameter = ClassParameter(varName, varType, expr)
                currentMethod.parameters.add(parameter)
            }

            is KtCallExpression -> {
                processPsiElement(expr)
            }


            is KtDotQualifiedExpression,
            is KtSafeQualifiedExpression -> {
                processPsiElement(expr)
            }

            is KtBinaryExpression -> {
                if (expr.operationToken == KtTokens.EQ) {

                    val leftExpression = expr.left ?: error("Left-hand side missing")
                    val rightExpression = expr.right ?: error("Right-hand side missing")

                    leftExpression.text
                    rightExpression.text


                    // 1️⃣ обрабатываем правую часть (источник)
                    val source = processPsiElement(rightExpression, null)
                            ?: VariableInfo(rightExpression.text, unknownType)

                    // 2️⃣ обрабатываем левую часть (цель)
                    val target = processPsiElement(leftExpression, null)
                            ?: VariableInfo(leftExpression.text, unknownType)

                    // 3️⃣ фиксируем assignment
                    val expr = AssignmentExpression(
                        target = target,
                        source = source,
                        operationType = AssigmentExpressionType.FieldFromField,
                        isParent = true
                    )

                    currentMethod.fullExpressions.add(expr)
                }
            }
            is KtUnaryExpression -> {}
            is KtReturnExpression -> {}
            is KtThrowExpression -> {}

            is KtReturnExpression -> {}
        }
    }

    fun processPsiElement(
        element: PsiElement,
        context: VariableInfo? = null
    ): VariableInfo? {

        var variable: VariableInfo? = null

        if (element is KtNameReferenceExpression)
            variable = getVariableOrField(element)

        else if(element is KtCallExpression){

            val method = if (context is MethodInfo) {
                context
            } else {
                MethodInfo(
                    receiverName = context?.name ?: "this",
                    receiverClass = context?.type ?: mainClass.name
                )
            }

            processMethod(element , method)

            val (tmpName, _) = variableStorage.add(method.rawContent)

            val target = VariableInfo(tmpName, method.type)

            currentMethod.fullExpressions.add(
                AssignmentExpression(
                    target = target,
                    source = method,
                    operationType = AssigmentExpressionType.FieldFromVariable,
                    isParent = true
                )
            )

            // 5️⃣ возвращаем результат вызова
            variable = target
        }

        else if (element is KtDotQualifiedExpression || element is KtSafeQualifiedExpression) {

            val dotExpression = element as KtQualifiedExpression

            val leftExpressionTemp =
                processPsiElement(dotExpression.receiverExpression, context)
                    ?: VariableInfo(dotExpression.receiverExpression.text, unknownType )

            val rightExpression = dotExpression.selectorExpression ?: return leftExpressionTemp

            val rightExpressionTemp =
                processPsiElement(rightExpression, leftExpressionTemp)
                    ?: leftExpressionTemp

            variable =  rightExpressionTemp
        }

        return variable
    }


    /////Helpers

    fun processMethod(expression: KtCallExpression, method: MethodInfo?) {
//Metho
        val methodName = expression.calleeExpression?.text ?: "" // мя метода

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

            VariableInfo(paramName, paramType)
        }

        val processedMethod = if (method != null) {
            method.name = methodName
            method.parameters = params.toMutableList()
            method.rawContent = getRawCallText(expression)

            method
        } else {
            MethodInfo(
                name = methodName,
                type = unknownType,
                parameters = params.toMutableList(),
                rawContent = getRawCallText(expression)
            )
        }


        val methodType = typeResolver.getMethodReturnType(processedMethod) ?: unknownType
        processedMethod.type = methodType
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

    fun getVariableOrField(expr: KtExpression): VariableInfo? {
        var variable: VariableInfo? = null

        if (expr is KtNameReferenceExpression) {
            variable = getVariableInfoFromKNamedExpression(expr)
        } else if (expr is KtDotQualifiedExpression || expr is KtSafeQualifiedExpression) {
            variable = getFieldInfoFromDotQualified(expr)
        }

        return variable
    }


    private fun getVariableInfoFromKNamedExpression(variableExpr: KtNameReferenceExpression): VariableInfo {
        val name = variableExpr.getReferencedName()

        val type = typeResolver.getVariableType(name) ?: unknownType

        return VariableInfo(name, type)
    }


    private fun getFieldInfoFromDotQualified(expression: KtExpression): FieldInfo? {
        val fieldExpression = when (expression) {
            is KtDotQualifiedExpression -> expression
            is KtSafeQualifiedExpression -> expression
            else -> null
        } ?: return null

        val receiverName = fieldExpression.receiverExpression.text
        val fieldName = fieldExpression.selectorExpression?.text ?: unknownType

        var type = typeResolver.getTypeByClassAndField(receiverName, fieldName)

        if(type == null) {

            var variableType = typeResolver.getVariableType(receiverName)

            if (variableType != null)
                type = typeResolver.getTypeByClassAndField(variableType, fieldName)
        }


        return FieldInfo(
            classType = type?:unknownType,
            className = receiverName,
            name = fieldName,
            type = type?:unknownType
        )
    }
}