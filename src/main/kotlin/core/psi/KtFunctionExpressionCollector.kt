package org.example.core.psi

import org.example.core.interfaces.IExpressionTypeResolver
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.linking.ExpressionTypeResolver
import org.example.data.symbol.*
import org.example.data.symbol.expression.VariableAssignmentExpression
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

class KtFunctionExpressionCollector {

    val tmpList = mutableListOf<Pair<String, String>>()
// Добавление
    var tmpCounter = 1

    private lateinit var currentClassMethod: ClassMethod
    private lateinit var searchEngine: IProjectSearchEngine
    private lateinit var mainClass: KotlinClass
    private lateinit var typeResolver: IExpressionTypeResolver
//
    fun getLastTempRecordByKey(key: String): Pair<String, String>? {
        return tmpList.asReversed().firstOrNull { it.first == key }
    }

    fun getLastTempRecordByValue(value: String): Pair<String, String>? {
        return tmpList.asReversed().firstOrNull { it.second == value }
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




    private fun buildVariableAssignmentExpression(currentExpression: KtCallExpression, target: VariableInfo?): VariableAssignmentExpression {
        //receiver
        val receiverExpression = (currentExpression.parent as? KtDotQualifiedExpression)?.receiverExpression
            ?: (currentExpression.parent as? KtSafeQualifiedExpression)?.receiverExpression

        val receiverText: String? = receiverExpression?.text
        val isInternalCall = receiverText == null

        var receiveRecord = receiverText?.let {  getLastTempRecordByValue(it)?.first}

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
                        tmpList.asReversed().firstOrNull { it.second == innerText }.toString() ?: rawText
                    } else rawText
                }

                else -> {
                    var customParam = getLastTempRecordByValue(rawText) ?: getLastTempRecordByValue(rawText)

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
            returnType = "Unit",
            parameters = params.toMutableList(),
            rawContent = getRawCallText(currentExpression)
        )

        val expression = VariableAssignmentExpression(
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

        if (expression.method.returnType.isNullOrEmpty() || expression.method.returnType == "_") {
            expression.method.returnType = methodReturnType ?: "Unit"
        }
        
        if (methodReturnType.isNullOrEmpty() || methodReturnType == "_")
            expression.target?.type?.let {expression.method.returnType = it  }
        else
            expression.target?.type = expression.method.returnType

        return expression
    }


    //psi element - это все что угодно!!!
    private fun handlePsiElement(currentElement: PsiElement, callingContext: VariableInfo?=null) {

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





            //main // a=b(), b(),c.b()
            is KtCallExpression -> {
                // Сначала обрабатываем аргументы рекурсивно
                currentElement.valueArguments.forEach { arg ->
                    arg.getArgumentExpression()?.let { handlePsiElement(it, callingContext) }
                }

                val receiverExpression = (currentElement.parent as? KtDotQualifiedExpression)?.receiverExpression
                    ?: (currentElement.parent as? KtSafeQualifiedExpression)?.receiverExpression

                // Для сложных выражений, включающих вызовы, лучше проверять:
                var currentTarget = callingContext?: null

                // Имя метода — сам вызов
                val expression = buildVariableAssignmentExpression(currentElement, currentTarget)

                currentClassMethod.fullExpressions.add(expression)


                //process inners
                val isComplex =
                    receiverExpression is KtCallExpression || receiverExpression is KtDotQualifiedExpression || receiverExpression is KtSafeQualifiedExpression

                val isInnerCall = isInnerCall(currentElement)

                if (isComplex || isInnerCall) {
                    val value = "tmp${tmpCounter++}"
                    tmpList.add(value to "${expression.receiver!!.name}.${expression.method.rawContent}" )
                    expression.target!!.name = value
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

    fun isInnerCall(call: KtCallExpression): Boolean {
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