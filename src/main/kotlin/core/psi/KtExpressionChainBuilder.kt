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
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

class KtExpressionChainBuilder(
    private val currentMethod: ClassMethod,
    private val mainClass: KotlinClass,
    private val searchEngine: IProjectSearchEngine,

) {

    private var typeResolver: IExpressionTypeResolver = ExpressionTypeResolver(searchEngine, mainClass, currentMethod)
    private val variableStorage: ITemporaryVariableStorage = TemporaryVariableStorage()

    val unknownType = "unknown"

    fun collectExpressions() {
        currentMethod.function.bodyExpression?.let { handlePsiElement(it) } // it: KtExpression

        print(1)
    }


    fun handlePsiElement(currentElement: PsiElement, contextVar: VariableInfo? = null): VariableInfo? {

        when (currentElement) {

            is KtIfExpression -> {
                currentElement.then?.let { handlePsiElement(it, contextVar) }
                currentElement.`else`?.let { handlePsiElement(it, contextVar) }
            }

            is KtWhenExpression -> {
                currentElement.entries.forEach { entry ->
                    entry.expression?.let { handlePsiElement(it, contextVar) }
                }
            }

            is KtForExpression -> {
                currentElement.body?.let { handlePsiElement(it, contextVar) }
            }

            is KtWhileExpression -> {
                currentElement.body?.let { handlePsiElement(it, contextVar) }
            }

            is KtDoWhileExpression -> {
                currentElement.body?.let { handlePsiElement(it, contextVar) }
            }

            is KtTryExpression -> {
                handlePsiElement(currentElement.tryBlock, contextVar)

                currentElement.catchClauses.mapNotNull { it.catchBody?.let { handlePsiElement(it, contextVar) } }
                    .lastOrNull()
                currentElement.finallyBlock?.finalExpression?.let { handlePsiElement(it, contextVar) }
            }

            is KtNameReferenceExpression -> {
                val existingVar = getVariableOrField(currentElement)

                if (existingVar.type == unknownType)
                    existingVar.type = typeResolver.getVariableType(existingVar.name) ?: unknownType

                existingVar
            }

            is KtProperty -> {
                val varName = currentElement.name ?: unknownType
                val varType = currentElement.typeReference?.text ?: unknownType

                val property = ClassProperty(varName, varType, currentElement)
                currentMethod.properties.add(property)

                val callingVar = VariableInfo(varName, varType)

                return callingVar
            }

            is KtParameter -> {
                val varName = currentElement.name ?: unknownType
                val varType = currentElement.typeReference?.text ?: unknownType

                val parameter = ClassParameter(varName, varType, currentElement)
                currentMethod.parameters.add(parameter)

                val callingVar = VariableInfo(varName, varType)

                return callingVar
            }


            is KtCallExpression -> {

                for (arg in currentElement.valueArguments) {

                    val expression = arg.getArgumentExpression()

                    if (expression != null)
                        handlePsiElement(expression, null)

                }

                val method = if (contextVar is MethodInfo) {
                    contextVar as MethodInfo
                } else {
                    MethodInfo()
                }


                // 3️⃣ источник
                processMethod(currentElement, method)

                val pair = variableStorage.add(method.rawContent)

                // Сохраняем выражение
                val expr = AssignmentExpression(
                    target = VariableInfo(pair.first, method.type),
                    source = method,
                    operationType = AssigmentExpressionType.FieldFromVariable,
                    isParent = true
                )

                currentMethod.fullExpressions.add(expr)

                // Возвращаем target
                expr.target
            }

            is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {

                val qualifiedExpr = currentElement as KtQualifiedExpression

                val receiverExpr: KtExpression? = qualifiedExpr.receiverExpression
                val selectorExpr = qualifiedExpr.selectorExpression


                val (tmpName, _) = variableStorage.add("this")

                val receiverVar: VariableInfo =
                    processSelectorExpression(receiverExpr, VariableInfo(tmpName, mainClass.name)) ?: VariableInfo(
                        tmpName,
                        mainClass.name
                    )

                val result: VariableInfo? = processSelectorExpression(selectorExpr, receiverVar)
                result
            }


            is KtBinaryExpression -> {
                if (currentElement.operationToken != KtTokens.EQ) {
                    val (tmpName, _) = variableStorage.add("unknown_binary")
                    return VariableInfo(tmpName, "_")
                }

                val lhs = currentElement.left ?: error("Left-hand side missing")
                val rhs = currentElement.right ?: error("Right-hand side missing")

                val lhsVar = handlePsiElement(lhs)


                //lleft
                var lhsResolved: VariableInfo? = null

                if (lhsVar != null) {
                    lhsResolved = lhsVar
                } else {
                    val (tmpName, _) = variableStorage.add(lhs.text)
                    lhsResolved = VariableInfo(tmpName, "_")
                }

                val rhsResolved = when (rhs) {
                    is KtCallExpression, is KtDotQualifiedExpression, is KtSafeQualifiedExpression ->
                        processSelectorExpression(rhs, lhsResolved) // lhsResolved как contextVar
                    else -> handlePsiElement(rhs) ?: run {
                        val (tmpName, _) = variableStorage.add(rhs.text)
                        VariableInfo(tmpName, "_")
                    }
                }

                val expr = AssignmentExpression(
                    target = lhsResolved,
                    source = rhsResolved ?: VariableInfo((variableStorage.add(rhs.text)).first, "_"),
                    operationType = AssigmentExpressionType.FieldFromField,
                    isParent = true
                )
                currentMethod.fullExpressions.add(expr)

                lhsResolved
            }

            is KtUnaryExpression -> {
                currentElement.baseExpression?.let { handlePsiElement(it, contextVar) } ?: run {
                    val (tmpName, _) = variableStorage.add("unary_tmp")
                    VariableInfo(tmpName, "_")
                }
            }

            is KtBinaryExpressionWithTypeRHS -> {
                val leftVar = currentElement.left?.let { handlePsiElement(it, contextVar) }
                val rightVar = currentElement.right?.let { handlePsiElement(it, contextVar) }

                leftVar ?: rightVar ?: run {
                    val (tmpName, _) = variableStorage.add("binaryType_tmp")
                    VariableInfo(tmpName, "_")
                }
            }

            is KtIsExpression -> {
                currentElement.leftHandSide?.let { handlePsiElement(it, contextVar) } ?: run {
                    val (tmpName, _) = variableStorage.add("is_tmp")
                    VariableInfo(tmpName, "_")
                }
            }

            is KtLambdaExpression -> {
                currentElement.bodyExpression?.let { handlePsiElement(it, contextVar) } ?: run {
                    val (tmpName, _) = variableStorage.add("lambda_tmp")
                    VariableInfo(tmpName, "_")
                }
            }

            is KtArrayAccessExpression -> {
                handlePsiElement(currentElement.arrayExpression as PsiElement, contextVar)
                currentElement.indexExpressions.forEach { handlePsiElement(it, contextVar) }

                val (tmpName, _) = variableStorage.add("array_tmp")
                VariableInfo(tmpName, "_")
            }

            is KtBlockExpression -> {
                var lastVar: VariableInfo? = null
                currentElement.statements.forEach { lastVar = handlePsiElement(it, contextVar) }

                lastVar ?: run {
                    val (tmpName, _) = variableStorage.add("block_tmp")
                    VariableInfo(tmpName, "_")
                }
            }

            else -> {
                var lastVar: VariableInfo? = null
                currentElement.children.forEach { lastVar = handlePsiElement(it, contextVar) }

                lastVar ?: run {
                    val (tmpName, _) = variableStorage.add("unknown_tmp")
                    VariableInfo(tmpName, "_")
                }
            }
        }

        return null
    }

    //helper

    // вложенный?
    fun isNestedExpression(expr: KtExpression?): Boolean {
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

        val processedMethod =  if (method != null) {
            method.name = methodName
            method.parameters = params.toMutableList()
            method.rawContent = getRawCallText(expression)

            method
        }
        else {
            MethodInfo(
                name = methodName,
                type = unknownType,
                parameters = params.toMutableList(),
                rawContent = getRawCallText(expression)
            )
        }


        val methodType = typeResolver.getMethodReturnType(processedMethod)?:unknownType
        processedMethod.type = methodType
    }

    fun getVariableOrField(expr: KtExpression): VariableInfo {
        val UNKNOWN_TYPE = "_"

        return when (expr) {
            is KtNameReferenceExpression ->
                getVariable(expr) ?: VariableInfo(variableStorage.add(expr.text).first, UNKNOWN_TYPE)

            is KtDotQualifiedExpression, is KtSafeQualifiedExpression ->
                getField(expr)?.let { FieldInfoToVariableInfo(it) }
                    ?: VariableInfo(variableStorage.add(expr.text).first, UNKNOWN_TYPE)

            else -> VariableInfo(variableStorage.add(expr.text).first, UNKNOWN_TYPE)
        }
    }


    // Помощник для конвертации FieldInfo в VariableInfo
    fun FieldInfoToVariableInfo(field: FieldInfo): VariableInfo {
        return VariableInfo(field.name, field.type)
    }


    fun getVariable(variableExpr: KtNameReferenceExpression): VariableInfo {
        val name = variableExpr.getReferencedName()

        val type = typeResolver.getVariableType(name) ?: "_"
        val target = VariableInfo(name, type)

        return target
    }

    fun getField(expression: KtExpression): FieldInfo? {
        val qualified = when (expression) {
            is KtDotQualifiedExpression -> expression
            is KtSafeQualifiedExpression -> expression
            else -> null
        } ?: return null

        val receiverName = qualified.receiverExpression.text
        val fieldName = qualified.selectorExpression?.text ?: "_"
        val type = typeResolver.getFieldType(receiverName, fieldName) ?: "_"

        return FieldInfo(
            classType = type,
            className = receiverName,
            name = fieldName,
            type = type
        )
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


    private fun processSelectorExpression(
        selectorExpr: KtExpression?,
        receiverVar: VariableInfo?
    ): VariableInfo?

    {
        if (selectorExpr == null) return receiverVar

        if (selectorExpr is KtCallExpression) {


            val methodInfo = MethodInfo(
                receiverName = receiverVar.name,
                receiverClass = receiverVar.type
            )

            // parent = parent (тот, кто ждёт результат), receiver = receiverVar (через кого идёт вызов)
            handlePsiElement(selectorExpr, methodInfo)
        }
        else if (selectorExpr is KtDotQualifiedExpression || selectorExpr is KtSafeQualifiedExpression) {

                val resolvedField = getVariableOrField(selectorExpr.receiverExpression ?: selectorExpr)

                if (isNestedExpression(selectorExpr)) {
                    val (tmpName, _) = variableStorage.add(resolvedField.name)
                    val tmpVar = VariableInfo(tmpName, resolvedField.type)

                    val expr = AssignmentExpression(
                        target = tmpVar,
                        source = resolvedField,
                        operationType = AssigmentExpressionType.FieldFromVariable,
                        isParent = true
                    )
                    currentMethod.fullExpressions.add(expr)

                    selectorExpr.selectorExpression?.let {
                        handlePsiElement(it, tmpVar)
                    }
                    tmpVar
                } else {
                    resolvedField
                }
            }

            else -> receiverVar
        }
    }

}