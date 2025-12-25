package org.example.core.psi

import org.example.core.interfaces.IExpressionTypeResolver
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.linking.ExpressionTypeResolver
import org.example.data.symbol.*
import org.example.data.symbol.enum.AssigmentExpressionType
import org.example.data.symbol.expression.AssignmentExpression
import org.example.data.symbol.expression.FieldFromVariableExpression
import org.example.data.symbol.expression.FieldInfo
import org.example.data.symbol.expression.FieldFromFieldExpression
import org.example.data.symbol.expression.MethodInfo
import org.example.data.symbol.expression.VariableFromMethodExpression
import org.example.data.symbol.expression.VariableInfo
import org.example.data.symbol.expression.VariableFromFieldExpression
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

class KtExpressionChainBuilder2 {

    private lateinit var currentClassMethod: ClassMethod
    private lateinit var searchEngine: IProjectSearchEngine
    private lateinit var mainClass: KotlinClass
    private lateinit var typeResolver: IExpressionTypeResolver

    private val variableStorage = TemporaryVariableStorage()

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


    // buildFieldAssignmentExpression
    private fun buildFieldAssignmentExpression(
        left: KtDotQualifiedExpression,
        right: KtExpression
    ): FieldFromVariableExpression {

        // Target (поле a.b)
        val receiverExpr = left.receiverExpression
        val fieldExpr = left.selectorExpression as? KtNameReferenceExpression

        val receiverName = receiverExpr.text
        val receiverType = typeResolver.getReceiverType(receiverName) ?: receiverName

        val fieldName = fieldExpr?.getReferencedName() ?: "_"
        val fieldType = typeResolver.getFieldType(receiverType, fieldName)?: fieldName

        val targetField = FieldInfo(
            className = receiverType,
            name = fieldName,
            type = fieldType
        )

        // Source (вызов метода RHS)
        val source: VariableInfo? = if (right is KtCallExpression) {
            val callExpr = buildVariableAssignmentExpression(right, null)
//to do add here
            currentClassMethod.fullExpressions.add(callExpr)
            val data = variableStorage.add( callExpr.method.rawContent)
            VariableInfo(data.first, callExpr.method.type)
        } else {
            null
        }

        return FieldFromVariableExpression(
            target = targetField,
            source = source!!
        )
    }


    private fun buildVariableAssignmentExpression(currentExpression: KtCallExpression, target: VariableInfo?): VariableFromMethodExpression {
        //receiver
        val receiverExpression = (currentExpression.parent as? KtDotQualifiedExpression)?.receiverExpression
            ?: (currentExpression.parent as? KtSafeQualifiedExpression)?.receiverExpression

        val receiverText: String? = receiverExpression?.text
        val isInternalCall = receiverText == null

        var receiveRecord = receiverText?.let {  variableStorage.getLastByValue(it)?.first}

        var receiverName = receiveRecord ?: when (receiverExpression) {
            is KtNameReferenceExpression -> receiverExpression.getReferencedName()
            is KtThisExpression -> "this"
            else -> receiverText
        }
        receiverName = receiverName?.replace("this.", "")


        val receiver = if(receiverName!=null) {
            val receiverType = typeResolver.getReceiverType(receiverName) ?: "_"

            val result = if (isInternalCall)
                VariableInfo("this", mainClass.name)
            else
                VariableInfo(receiverName, receiverType)

            result
        }
        else
            null
//Method
        val methodName = currentExpression.calleeExpression?.text ?: "" // мя метода

        val params = currentExpression.valueArguments.mapNotNull { arg ->
            val argExpr = arg.getArgumentExpression()
            val rawText = argExpr?.text ?: "?"

            // Определяем имя параметра с подстановкой tmpMap для сложных выражений
            var paramName = when (argExpr) {
                is KtCallExpression -> {
                    val innerReceiver = (argExpr.parent as? KtDotQualifiedExpression)?.receiverExpression
                    val innerText = innerReceiver?.text

                    if (innerText != null) {
                        variableStorage.getLastByValue(innerText) ?.second ?: rawText
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
            rawContent = getRawCallText(currentExpression)
        )

        val expression = VariableFromMethodExpression(
            target = target,
            receiver = receiver,
            method = method,
        )

        var methodReturnType = typeResolver.getMethodOrFieldReturnType(expression)

        if (methodReturnType == null) {

            methodReturnType = if (expression.receiver == null || expression.receiver.name == "this") {
                searchEngine.findMethodByClassNameAndMethodNameAndParams(
                    mainClass.name,
                    expression.method.name,
                    expression.method.parameters.map { it.type }) as String?
            } else {
                searchEngine.findMethodByClassNameAndMethodNameAndParams(
                    expression.receiver.type,
                    expression.method.name,
                    expression.method.parameters.map { it.type }) as String?
            }
        }

        expression.method.type = methodReturnType ?: "Unit"

        if (methodReturnType.isNullOrEmpty() || methodReturnType == "_")
            expression.target?.type?.let {expression.method.type = it  }
        else
            expression.target?.type = expression.method.type

        return expression
    }




    private fun buildVariableToFieldAssignmentExpression(
        left: KtNameReferenceExpression,  // a
        right: KtDotQualifiedExpression   // b.c или более длинная цепочка
    ): VariableFromFieldExpression {

        // --- Target (переменная a) ---
        val targetName = left.getReferencedName()
        val targetType = typeResolver.getReceiverType(targetName) ?: "_"
        val targetVar = VariableInfo(targetName, targetType)

        // --- Source (цепочка b.c.d...) ---
        val sourceParts = mutableListOf<String>()
        var current: KtExpression? = right
        while (current != null) {
            when (current) {
                is KtDotQualifiedExpression -> {
                    val selector = current.selectorExpression as? KtNameReferenceExpression
                    selector?.let { sourceParts.add(it.getReferencedName()) }
                    current = current.receiverExpression
                }
                is KtNameReferenceExpression -> {
                    sourceParts.add(current.getReferencedName())
                    current = null
                }
                else -> current = null
            }
        }
        sourceParts.reverse() // чтобы было в порядке b.c.d
        val sourceFieldName = sourceParts.joinToString(".")
        val sourceType = typeResolver.getReceiverType(sourceParts.first()) ?: "_"

        val sourceField = FieldInfo(
            className = sourceParts.first(),
            name = sourceFieldName,
            type = sourceType
        )

        return VariableFromFieldExpression(
            target = targetVar,
            source = sourceField
        )
    }

    private fun buildFieldToFieldAssignmentExpression(
        left: KtDotQualifiedExpression,
        right: KtDotQualifiedExpression
    ): FieldFromFieldExpression {

        val lhsReceiver = left.receiverExpression.text
        val lhsField = (left.selectorExpression as? KtNameReferenceExpression)?.getReferencedName() ?: "_"

        val rhsReceiver = right.receiverExpression.text
        val rhsField = (right.selectorExpression as? KtNameReferenceExpression)?.getReferencedName() ?: "_"

        val lhsFieldInfo = FieldInfo(lhsReceiver, lhsField, typeResolver.getReceiverType(lhsReceiver) ?: "_")
        val rhsFieldInfo = FieldInfo(rhsReceiver, rhsField, typeResolver.getReceiverType(rhsReceiver) ?: "_")

        return FieldFromFieldExpression(
            target = lhsFieldInfo,
            source = rhsFieldInfo
        )
    }
    //helper
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


    fun buildAssignmentExpression(lhs: KtExpression?, rhs: KtExpression?, isParent: Boolean = true): AssignmentExpression {
        val target: Any? = when(lhs) {
            is KtNameReferenceExpression -> VariableInfo(lhs.getReferencedName(), "_")
            is KtDotQualifiedExpression -> FieldInfo(
                className = lhs.receiverExpression.text,
                name = lhs.selectorExpression?.text ?: "_",
                type = "_"
            )
            else -> null
        }

        val source: Any? = when(rhs) {
            is KtCallExpression -> {
                val receiverExpr = (rhs.parent as? KtDotQualifiedExpression)?.receiverExpression
                val receiverInfo = receiverExpr?.let {
                    FieldInfo(it.text, "_", "_")
                }
                MethodInfo(
                    name = rhs.calleeExpression?.text ?: "_",
                    type = "Unit",
                    parameters = mutableListOf(),
                    receiverName = receiverInfo?.name?:"",
                    receiverClass = receiverInfo?.name?:"",
                )
            }
            is KtNameReferenceExpression -> VariableInfo(rhs.getReferencedName(), "_")
            is KtDotQualifiedExpression -> FieldInfo(
                className = rhs.receiverExpression.text,
                name = rhs.selectorExpression?.text ?: "_",
                type = "_"
            )
            else -> null
        }

        return AssignmentExpression(
            target = target,
            source = source,
            operationType = AssigmentExpressionType.FieldFromField,
            isParent = isParent
        )
    }

    //psi element - это все что угодно!!!
    private fun handlePsiElement(currentElement: PsiElement, callingContext: Any?=null) {

        when (currentElement) {
            is KtProperty -> {
                val varName = currentElement.name ?: "__no_name__"
                val varType = currentElement.typeReference?.text ?: "_"

                val property = ClassProperty(varName, varType, currentElement)

                currentClassMethod.properties.add(property)

                //initializer = это = выражение.
                // т.у property = initializer.
                val callingContextVariable = VariableInfo(varName, varType)

                currentElement.initializer?.let { expression ->
                    handlePsiElement(expression, callingContextVariable)
                }
            }

            is KtParameter -> {
                val varName = currentElement.name ?: "__no_name__"
                val varType = currentElement.typeReference?.text ?: "_"

                // Создаём объект для хранения информации о параметре
                val parameter = ClassParameter(varName, varType, currentElement)

                // Добавляем параметр в parentMethod.properties (или отдельный список параметров, если нужно)
                currentClassMethod.parameters.add(parameter)

                val callingContextVariable = VariableInfo(varName, varType)

                // Если есть значение по умолчанию, рекурсивно обрабатываем его как выражение
                currentElement.defaultValue?.let { defaultExpr ->
                    handlePsiElement(defaultExpr,callingContextVariable)
                }
            }


            is KtBinaryExpression -> {
                if (currentElement.operationToken == KtTokens.EQ) {
                    val left = currentElement.left
                    val right = currentElement.right ?: return

                    val f = buildAssignmentExpression(left, right)
                    val leftText = left?.text
                    val rightText = right?.text
                    val efefe = isNestedExpression(left)
                    val efe2fe = isNestedExpression(right)
                    when {
                        // 1. LHS поле, RHS вызов метода
                        left is KtDotQualifiedExpression && right is KtCallExpression -> {
                            val expression = buildFieldAssignmentExpression(left, right)
                            currentClassMethod.fullExpressions.add(expression)

                            // tmp для RHS, чтобы вложенные вызовы видели контекст
                            val sourceTmp = expression.source
                            handlePsiElement(right, sourceTmp)
                        }

                        // 2. LHS переменная, RHS цепочка полей
                        left is KtNameReferenceExpression && right is KtDotQualifiedExpression -> {
                            val expression = buildVariableToFieldAssignmentExpression(left, right)
                            currentClassMethod.fullExpressions.add(expression)
                        }

                        // 3. LHS переменная, RHS вызов метода
                        left is KtNameReferenceExpression && right is KtCallExpression -> {
                            val expression = buildVariableAssignmentExpression(
                                right,
                                VariableInfo(left.getReferencedName(), "_")
                            )
                            currentClassMethod.fullExpressions.add(expression)
                        }

                        // 4. LHS поле, RHS поле (FieldToFieldAssignment)
                        left is KtDotQualifiedExpression && right is KtDotQualifiedExpression -> {
                            val expression = buildFieldToFieldAssignmentExpression(left, right)
                            currentClassMethod.fullExpressions.add(expression)
                        }

                        else -> {
                            // fallback — можно просто рекурсивно пройтись
                            left?.let { handlePsiElement(it, callingContext) }
                            handlePsiElement(right, callingContext)
                        }
                    }
                    return
                }
            }






            //main // a=b(), b(),c.b()
            is KtCallExpression ->  {
                if (isRhsOfBinaryAssign(currentElement))
                    return // этот вызов будет обработан через buildFieldAssignmentExpression

                currentElement.text
                // Сначала обрабатываем аргументы рекурсивно
                currentElement.valueArguments.forEach { arg ->
                    arg.getArgumentExpression()?.let { handlePsiElement(it, callingContext) }
                }

                val receiverExpression = (currentElement.parent as? KtDotQualifiedExpression)?.receiverExpression
                    ?: (currentElement.parent as? KtSafeQualifiedExpression)?.receiverExpression

                // Для сложных выражений, включающих вызовы, лучше проверять:
                val currentTarget: VariableInfo? = callingContext as? VariableInfo

                // Имя метода — сам вызов
                val expression = buildVariableAssignmentExpression(currentElement, currentTarget)

                currentClassMethod.fullExpressions.add(expression)


                //process inners
                val isComplex =
                    receiverExpression is KtCallExpression || receiverExpression is KtDotQualifiedExpression || receiverExpression is KtSafeQualifiedExpression

                val isInnerCall = isInnerCall(currentElement)

                if (isComplex || isInnerCall) {
                    val (tmpName, tmpValue) = variableStorage.add("${expression.receiver?.name}.${expression.method.rawContent}")
                    expression.target?.name = tmpName
                }
            }

            ///dot
            is KtDotQualifiedExpression -> {
                // Receiver становится parent для selector
                currentElement.receiverExpression?.let { handlePsiElement(it, callingContext) }
                currentElement.selectorExpression?.let { handlePsiElement(it, callingContext) }
            }

            is KtSafeQualifiedExpression -> {
                currentElement.receiverExpression?.let { handlePsiElement(it, callingContext) }
                currentElement.selectorExpression?.let { handlePsiElement(it, callingContext) }
            }

            ///others
            is KtBlockExpression -> currentElement.statements.forEach { handlePsiElement(it, callingContext) }
            is KtForExpression -> currentElement.body?.let { handlePsiElement(it, callingContext) }
            is KtWhileExpression -> currentElement.body?.let { handlePsiElement(it, callingContext) }
            is KtIfExpression -> {
                currentElement.then?.let { handlePsiElement(it, callingContext) }
                currentElement.`else`?.let { handlePsiElement(it, callingContext) }
            }
            is KtTryExpression -> {
                handlePsiElement(currentElement.tryBlock, callingContext)
                currentElement.catchClauses.forEach { it.catchBody?.let { handlePsiElement(it, callingContext) } }
                currentElement.finallyBlock?.finalExpression?.let { handlePsiElement(it, callingContext) }
            }
            else
                -> currentElement.children.forEach { handlePsiElement(it, callingContext) }
        }
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


    private fun isInnerCall(call: KtCallExpression): Boolean {
        val parentCall = call.parent?.getStrictParentOfType<KtCallExpression>() ?: return false

        return parentCall.valueArguments.any { arg ->
            arg.getArgumentExpression()?.isAncestor(call) == true
        }
    }


    fun collectExpressions(method: ClassMethod, kotlinClass: KotlinClass, searchEngine: IProjectSearchEngine) {

        currentClassMethod = method
        this.searchEngine = searchEngine
        this.mainClass = kotlinClass

        typeResolver = ExpressionTypeResolver(searchEngine, kotlinClass, method)
        val currentMethodFnNamedFunction = method.function

        currentMethodFnNamedFunction.bodyExpression?.let { handlePsiElement(it) } // it: KtExpression
    }
}