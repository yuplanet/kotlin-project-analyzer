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
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

class KtExpressionChainBuilder(
    private val currentMethod: ClassMethod,
    private val mainClass: KotlinClass,
    private val searchEngine: IProjectSearchEngine,

) {

    private var typeResolver: IExpressionTypeResolver = ExpressionTypeResolver(searchEngine, mainClass, currentMethod)
    private val variableStorage: ITemporaryVariableStorage = TemporaryVariableStorage()


    fun collectExpressions() {
        currentMethod.function.bodyExpression?.let { handlePsiElement(it) } // it: KtExpression
    }


    fun handlePsiElement(currentElement: PsiElement, callingContext: VariableInfo? = null) {

        when (currentElement) {

            is KtIfExpression -> {
                currentElement.then?.let { handlePsiElement(it, callingContext) }
                currentElement.`else`?.let { handlePsiElement(it, callingContext) }
            }

            is KtWhenExpression -> {
                currentElement.entries.forEach { entry ->
                    entry.expression?.let { handlePsiElement(it, callingContext) }
                }
            }

            is KtForExpression -> {
                currentElement.body?.let { handlePsiElement(it, callingContext) }
            }

            is KtWhileExpression -> {
                currentElement.body?.let { handlePsiElement(it, callingContext) }
            }

            is KtDoWhileExpression -> {
                currentElement.body?.let { handlePsiElement(it, callingContext) }
            }

            is KtTryExpression -> {
                handlePsiElement(currentElement.tryBlock, callingContext)
                currentElement.catchClauses.forEach { it.catchBody?.let { handlePsiElement(it, callingContext) } }
                currentElement.finallyBlock?.finalExpression?.let { handlePsiElement(it, callingContext) }
            }


            is KtProperty -> {
                val varName = currentElement.name ?: "__no_name__"
                val varType = currentElement.typeReference?.text ?: "_"

                // Создаем объект свойства класса
                val property = ClassProperty(varName, varType, currentElement)
                currentMethod.properties.add(property)

                // VariableInfo для передачи как callingContext
                val callingVar = VariableInfo(varName, varType)

                // Если есть initializer (например, a = b())
                currentElement.initializer?.let { handlePsiElement(it, callingVar) }
            }

            is KtParameter -> {
                val varName = currentElement.name ?: "__no_name__"
                val varType = currentElement.typeReference?.text ?: "_"

                // Создаем объект параметра
                val parameter = ClassParameter(varName, varType, currentElement)
                currentMethod.parameters.add(parameter)

                val callingVar = VariableInfo(varName, varType)

                // Если есть значение по умолчанию
                currentElement.defaultValue?.let { handlePsiElement(it, callingVar) }
            }










            is KtCallExpression -> {
                if (isRhsOfBinaryAssign(currentElement))
                    return // этот вызов будет обработан через buildFieldAssignmentExpression

                val callExpr = currentElement
                val isNestedCall = isNestedExpression(callExpr)

                // 1️⃣ Обрабатываем receiver (если есть)
                val receiverExpr = (callExpr.parent as? KtDotQualifiedExpression)?.receiverExpression
                    ?: (callExpr.parent as? KtSafeQualifiedExpression)?.receiverExpression

                val callingReceiver: VariableInfo? = receiverExpr?.let { expr ->
                    if (isNestedExpression(expr)) {
                        // Рекурсивно обрабатываем вложенный receiver
                        handlePsiElement(expr, callingContext)
                        // Берём tmp-переменную из variableStorage
                        variableStorage.getLastByValue(expr.text)?.let { VariableInfo(it.first) }
                    } else {
                        getVariableOrField(expr)
                    }
                }

                // 2️⃣ Обрабатываем аргументы рекурсивно
                callExpr.valueArguments.forEach { arg ->
                    arg.getArgumentExpression()?.let { handlePsiElement(it, callingReceiver) }
                }

                // 3️⃣ Создаем AssignmentExpression для самого вызова
                val methodExpr = buildAssignmentExpression(null, callExpr)
                currentMethod.fullExpressions.add(methodExpr)

                // 4️⃣ Если вызов вложенный, создаём tmp и обновляем target
                if (isNestedCall) {
                    val (tmpName, _) = variableStorage.add(callExpr.text)
                    (methodExpr.target as? MethodInfo)?.name = tmpName
                }
            }



            is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {
                val qualifiedExpr = currentElement as KtQualifiedExpression

                val receiverExpr = qualifiedExpr.receiverExpression
                val selectorExpr = qualifiedExpr.selectorExpression

                // 1️⃣ Обрабатываем receiver рекурсивно
                val callingReceiver: VariableInfo? = if (isNestedExpression(receiverExpr)) {
                    handlePsiElement(receiverExpr, callingContext)
                    // Берем tmp-переменную после рекурсии
                    variableStorage.getLastByValue(receiverExpr.text)?.let { VariableInfo(it.first, "_") }
                } else {
                    getVariableOrField(receiverExpr)
                }

                // 2️⃣ Если selector — вызов метода, спускаемся в него
                if (selectorExpr is KtCallExpression) {
                    handlePsiElement(selectorExpr, callingReceiver)
                }

                // 3️⃣ Создаем FieldInfo для текущей dot-цепочки
                val fieldExpr = buildAssignmentExpression(null,qualifiedExpr)
                currentMethod.fullExpressions.add(fieldExpr)

                // 4️⃣ Если выражение вложенное — создаем tmp
                if (isNestedExpression(qualifiedExpr)) {
                    val tmpName = variableStorage.add(fieldExpr.source?.name)
                    fieldExpr.target?.name = tmpName
                }
            }


            is KtBinaryExpression -> {
                if (currentElement.operationToken != KtTokens.EQ)
                    return

                val left = currentElement.left
                val right = currentElement.right ?: return // чтобы a.b обрабатывало dot.Expression


                if (isNestedExpression(left)) {
                    handlePsiElement(left as KtExpression, null)
                }

                // RIGHT: обрабатываем
                val rhsSource: VariableInfo? = if (isNestedExpression(right)) {
                    // Рекурсивно спускаемся по RHS
                    handlePsiElement(right as KtExpression, null)
                    // После обработки RHS берём tmp-переменную из variableStorage
                    variableStorage.getLastByValue(right.text)?.let { VariableInfo(it.first) }
                } else {
                    when (right) {
                        is KtNameReferenceExpression -> getVariable(right)
                        is KtDotQualifiedExpression,
                        is KtSafeQualifiedExpression -> getField(right as KtExpression)
                        is KtCallExpression -> getMethod(right)
                        else -> null
                    }
                }

                // Создаём AssignmentExpression
                val expr = AssignmentExpression(
                    target = if (isNestedExpression(left)) variableStorage.getLastByValue(left.text)?.let { VariableInfo(it.first) } else getVariableOrField(left),
                    source = rhsSource,
                    operationType = AssigmentExpressionType.FieldFromField,
                    isParent = true
                )

                currentMethod.fullExpressions.add(expr)
            }


            is KtUnaryExpression -> {
                // Например, ++a, a--, !a
                currentElement.baseExpression?.let { handlePsiElement(it, callingContext) }
            }

            is KtBinaryExpressionWithTypeRHS -> {
                // Присвоение с указанием типа: val a: Int = ...
                currentElement.left?.let { handlePsiElement(it, callingContext) }
                currentElement.right?.let { handlePsiElement(it, callingContext) }
            }

            is KtIsExpression -> {
                // Проверка типа: a is String
                currentElement.leftHandSide?.let { handlePsiElement(it, callingContext) }
            }

            is KtLambdaExpression -> {
                // Обрабатываем тело лямбды
                currentElement.bodyExpression?.let { handlePsiElement(it, callingContext) }
            }

            is KtArrayAccessExpression -> {
                // Пример: arr[i] = ...
                handlePsiElement(currentElement.arrayExpression as PsiElement, callingContext)
                currentElement.indexExpressions.forEach { handlePsiElement(it, callingContext) }
            }

            is KtBlockExpression -> {
                // Проходим рекурсивно по всем выражениям в блоке
                currentElement.statements.forEach { handlePsiElement(it, callingContext) }
            }

            else -> {
                // Любой другой PsiElement — рекурсивно спускаемся по детям
                currentElement.children.forEach { handlePsiElement(it, callingContext) }
            }

        }
    }



    //Expression build
    fun buildAssignmentExpression(lhs: KtExpression?, rhs: KtExpression?): AssignmentExpression {

        var target: VariableInfo? = null

        if (lhs is KtNameReferenceExpression) {
            target = getVariable(lhs)
        } else if (lhs is KtDotQualifiedExpression || lhs is KtSafeQualifiedExpression) {
            target = getField(lhs)
        }

        var source: VariableInfo? = null
        if (rhs is KtCallExpression) {
            source = getMethod(rhs)
        } else if (rhs is KtNameReferenceExpression) {

            source = getVariable(rhs)

        } else if (rhs is KtDotQualifiedExpression || rhs is KtSafeQualifiedExpression) {
            source = getField(rhs)
        }

        val expr = AssignmentExpression(
            target = target,
            source = source,
            operationType = AssigmentExpressionType.FieldFromField,
            isParent = true
        )

        currentMethod.fullExpressions.add(expr)

        return expr
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


    fun getMethod(expression: KtCallExpression): MethodInfo {

        //receiver
        val receiverExpression = (expression.parent as? KtDotQualifiedExpression)?.receiverExpression
            ?: (expression.parent as? KtSafeQualifiedExpression)?.receiverExpression

        val receiverText: String? = receiverExpression?.text
        val isInternalCall = receiverText == null

        var receiveRecord = receiverText?.let { variableStorage.getLastByValue(it)?.first }

        var receiverName = receiveRecord ?: when (receiverExpression) {
            is KtNameReferenceExpression -> receiverExpression.getReferencedName()
            is KtThisExpression -> "this"
            else -> receiverText
        }
        receiverName = receiverName?.replace("this.", "")


        val receiver = if (receiverName != null) {
            val receiverType = typeResolver.getReceiverType(receiverName) ?: "_"

            val result = if (isInternalCall)
                VariableInfo("this", mainClass.name)
            else
                VariableInfo(receiverName, receiverType)

            result
        } else
            null
//Method
        val methodName = expression.calleeExpression?.text ?: "" // мя метода
        val params = expression.valueArguments.mapNotNull { arg ->
            val argExpr = arg.getArgumentExpression()
            val rawText = argExpr?.text ?: "?"

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
                    var customParam = variableStorage.getLastByValue(rawText)

                    customParam?.first ?: rawText
                }
            }


            if (paramName.isBlank()) return@mapNotNull null

            val paramType = typeResolver.getMethodParameterType(paramName) ?: "_"
            VariableInfo(paramName, paramType)
            // Пытаемся найти тип в уже известных FullExpression
        }

        val method = MethodInfo(
            name = methodName,
            type = "Unit",
            parameters = params.toMutableList(),
            rawContent = getRawCallText(expression)
        )

        return method
    }

    fun getVariableOrField(expr: KtExpression): VariableInfo? {
        return when (expr) {
            is KtNameReferenceExpression -> getVariable(expr)
            is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {
                getField(expr)?.let { FieldInfoToVariableInfo(it) }
            }
            else -> null
        }
    }

    // Помощник для конвертации FieldInfo в VariableInfo
    fun FieldInfoToVariableInfo(field: FieldInfo): VariableInfo {
        return VariableInfo(field.name, field.type)
    }


    fun getVariable(variableExpr: KtNameReferenceExpression):VariableInfo? {
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


    fun getRawCallText(call: KtCallExpression): String {
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


    private fun isRhsOfBinaryAssign(call: KtCallExpression): Boolean {
        var element: PsiElement? = call
        while (element != null) {
            val parent = element.parent
            if (parent is KtBinaryExpression &&
                parent.operationToken == KtTokens.EQ &&
                parent.right?.isAncestor(call) == true
            ) {
                return true
            }
            element = parent
        }
        return false
    }
}