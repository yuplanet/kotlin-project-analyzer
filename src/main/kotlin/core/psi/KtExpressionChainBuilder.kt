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


    fun collectExpressions() {
        currentMethod.function.bodyExpression?.let { handlePsiElement(it) } // it: KtExpression
    }


    fun handlePsiElement(currentElement: PsiElement, callingContext: VariableInfo? = null): VariableInfo? {

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

                val callingVar = VariableInfo(varName, varType)

                // Если есть initializer, рекурсивно обрабатываем и используем результат
                //val initializerVar = currentElement.initializer?.let { handlePsiElement(it, callingVar) }

                // Возвращаем VariableInfo для текущего свойства
                return callingVar
            }

            is KtParameter -> {
                val varName = currentElement.name ?: "__no_name__"
                val varType = currentElement.typeReference?.text ?: "_"

                val parameter = ClassParameter(varName, varType, currentElement)
                currentMethod.parameters.add(parameter)

                val callingVar = VariableInfo(varName, varType)

                    //val defaultValueVar = currentElement.defaultValue?.let { handlePsiElement(it, callingVar) }

                return callingVar
            }


            is KtCallExpression -> {

                val callExpr = currentElement
                val isNestedCall = isNestedExpression(callExpr)

                // 1️⃣ Рекурсивно обрабатываем receiver, если есть
                val receiverExpr = (callExpr.parent as? KtDotQualifiedExpression)?.receiverExpression
                    ?: (callExpr.parent as? KtSafeQualifiedExpression)?.receiverExpression

                val callingReceiver: VariableInfo = receiverExpr?.let { expr ->
                    when (expr) {
                        is KtCallExpression, is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {
                            handlePsiElement(expr, callingContext)
                            // tmp-переменная для receiver, если была создана
                            variableStorage.getLastByValue(expr.text)?.let { VariableInfo(it.first, "_") }
                                ?: VariableInfo("_", mainClass.name)
                        }
                        else -> getVariableOrField(expr) ?: VariableInfo(expr.text, "_")
                    }
                } ?: VariableInfo("this", mainClass.name) // если receiver нет, используем текущий класс как this

                // 2️⃣ Рекурсивно обрабатываем параметры метода
                callExpr.valueArguments.forEach { arg ->
                    arg.getArgumentExpression()?.let { handlePsiElement(it, callingReceiver) }
                }

                // 3️⃣ Создаем MethodInfo после того как параметры обработаны
                val methodInfo = getMethod(callExpr)

                // 4️⃣ Если вызов вложенный, создаём tmp для метода
                val targetVar: VariableInfo = if (isNestedCall) {
                    val (tmpName, _) = variableStorage.add(callExpr.text)
                    val tmpVar = VariableInfo(tmpName, "_")
                    // Сохраняем Expression: tmp = метод
                    val expr = AssignmentExpression(
                        target = tmpVar,
                        source = methodInfo,
                        operationType = AssigmentExpressionType.FieldFromVariable,
                        isParent = true
                    )
                    currentMethod.fullExpressions.add(expr)
                    tmpVar
                } else {
                    // Если не вложенный — просто возвращаем MethodInfo
                    methodInfo
                }

                // 🔹 Возвращаем tmp или MethodInfo на уровень выше
                targetVar
            }

            is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {
                val qualifiedExpr = currentElement as KtQualifiedExpression
                val receiverExpr = qualifiedExpr.receiverExpression
                val selectorExpr = qualifiedExpr.selectorExpression

                // 1️⃣ Резолвим receiver через handle, чтобы получить tmp/VariableInfo
                val callingReceiver: VariableInfo? = handlePsiElement(receiverExpr, callingContext)

// 2️⃣ Обрабатываем selector
                val result: VariableInfo = when (selectorExpr) {
                    is KtCallExpression -> {
                        handlePsiElement(selectorExpr, callingReceiver)
                            ?: error("Method call didn't return VariableInfo")
                    }

                    is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {
                        val resolvedField = getVariableOrField(selectorExpr.receiverExpression ?: selectorExpr)
                            ?: error("Field resolution failed")

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

                            selectorExpr.selectorExpression?.let { handlePsiElement(it, tmpVar) }
                            tmpVar
                        } else {
                            resolvedField
                        }
                    }

                    else -> callingReceiver ?: getVariableOrField(qualifiedExpr)
                    ?: error("Cannot resolve expression")
                }
            }

            is KtBinaryExpression -> {
                if (currentElement.operationToken != KtTokens.EQ) return null

                val lhs = currentElement.left ?: return null
                val rhs = currentElement.right ?: return null

                // 1️⃣ Рекурсивно резолвим обе части
                val lhsVar: VariableInfo? = handlePsiElement(lhs)
                val rhsVar: VariableInfo? = handlePsiElement(rhs)

                // 2️⃣ Если части были сложные, берём tmp из storage
                val lhsResolved = lhsVar ?: variableStorage.getLastByValue(lhs.text)?.let { VariableInfo(it.first) }
                val rhsResolved = rhsVar ?: variableStorage.getLastByValue(rhs.text)?.let { VariableInfo(it.first) }

                if (lhsResolved != null && rhsResolved != null) {
                    val expr = AssignmentExpression(
                        target = lhsResolved,
                        source = rhsResolved,
                        operationType = AssigmentExpressionType.FieldFromField,
                        isParent = true
                    )
                    currentMethod.fullExpressions.add(expr)
                }

                // 3️⃣ Возвращаем lhs для возможного использования выше
                lhsResolved
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
        return null
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

        // Если target всё ещё null, создаем tmp через storage
        if (target == null && rhs != null) {
            val tmpName = variableStorage.add("hs.") // возвращает имя tmp
            target = VariableInfo(tmpName.first, tmpName.second) // тип пока можно определить через IExpressionTypeResolver
        }

        val expr = AssignmentExpression(
            target = target,
            source = source!!,
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