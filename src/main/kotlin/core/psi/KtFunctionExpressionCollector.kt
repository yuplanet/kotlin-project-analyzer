package org.example.core.psi

import org.example.core.interfaces.IExpressionTypeResolver
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.linking.ExpressionTypeResolver
import org.example.data.symbol.*
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KtFunctionExpressionCollector {

    val tmpMap = mutableMapOf<String, String>()
    var tmpCounter = 1

    private lateinit var currentClassMethod: ClassMethod
    private lateinit var searchEngine: IProjectSearchEngine
    private lateinit var mainClass: KotlinClass
    private lateinit var typeResolver: IExpressionTypeResolver



    fun getRawCallText(call: KtCallExpression): String {
        var element: PsiElement = call

        while (
            element.parent is KtDotQualifiedExpression ||
            element.parent is KtSafeQualifiedExpression
        ) {
            element = element.parent
        }

        return element.text
    }



    private fun buildFullExpression(currentExpression: KtCallExpression, callingContext: VariableInfo): FullExpression {
        currentExpression.calleeExpression?.text

        //receiver
        val receiverExpression = (currentExpression.parent as? KtDotQualifiedExpression)?.receiverExpression
            ?: (currentExpression.parent as? KtSafeQualifiedExpression)?.receiverExpression

        val receiverName = receiverExpression?.text?.let { tmpMap[it] }
            ?: when (receiverExpression) {
                is KtNameReferenceExpression -> receiverExpression.getReferencedName()
                is KtThisExpression -> "this"
                else -> tmpMap.entries.firstOrNull { it.value == receiverExpression?.text }?.key
                    ?: receiverExpression?.text
                    ?: "this" // сюда не должно попасть сложное выражение, если tmpMap работает
            }

        val receiverType = typeResolver.getReceiverType(receiverName) ?: "_"
        val receiver = VariableInfo(receiverName, receiverType)


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
                    if (innerText != null && tmpMap.containsKey(innerText)) tmpMap[innerText] else rawText
                }

                else -> tmpMap.entries.firstOrNull { it.value == rawText }?.key ?: rawText
            }


            if (paramName.isNullOrBlank()) return@mapNotNull null

            val paramType = typeResolver.getMethodParameterType(paramName) ?: "_"
            VariableInfo(paramName, paramType)
            // Пытаемся найти тип в уже известных FullExpression
        }

        val method = MethodInfo(
            name = methodName,
            innerInvoke = false,
            returnType = "",
            parameters = params.toMutableList(),
            rawContent = getRawCallText(currentExpression)
        )

        val expression = FullExpression(
            target = callingContext,
            receiver = receiver,
            method = method,
        )

        var methodReturnType = typeResolver.getMethodOrFieldReturnType(expression)

        if (methodReturnType == null) {

            methodReturnType = searchEngine.findMethodByClassNameAndMethodNameAndParams(
                expression.receiver.type ?: "_",
                expression.method.name,
                expression.method.parameters.map { it.type }) as String?
        }

        if (expression.method.returnType.isNullOrEmpty() || expression.method.returnType == "_") {
            expression.method.returnType = methodReturnType ?: "_"
        }

        expression.target.type = expression.method.returnType

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


            //main
            is KtCallExpression -> {
                // Сначала обрабатываем аргументы рекурсивно
                currentElement.valueArguments.forEach { arg ->
                    arg.getArgumentExpression()?.let { handlePsiElement(it, callingContext) }
                }

                val receiverExpression = (currentElement.parent as? KtDotQualifiedExpression)?.receiverExpression
                    ?: (currentElement.parent as? KtSafeQualifiedExpression)?.receiverExpression

                val receiverText = receiverExpression?.text ?: "this" // имя serivce
    // Для сложных выражений, включающих вызовы, лучше проверять:

                    val isComplex =
                        receiverExpression is KtCallExpression || receiverExpression is KtDotQualifiedExpression || receiverExpression is KtSafeQualifiedExpression

                    if (isComplex){

                        val value = "tmp${tmpCounter++}"
                        tmpMap.getOrPut(value) { receiverText }



                    }
                var currentContext = callingContext
                    ?: VariableInfo(
                        tmpMap.entries.firstOrNull { it.value == receiverText }?.key ?: receiverText,
                        "" // to do find type
                    )

                val t  = getTarget(currentElement)

                // Имя метода — сам вызов
                val expression = buildFullExpression(currentElement, currentContext)

                currentClassMethod.fullExpressions.add(expression)

                val originalExpressionText = tmpMap[expression.target.name]

                if (originalExpressionText != null) {
                    val params = if (expression.method.parameters.isNotEmpty()) {
                        expression.method.parameters
                            .map { it.name.replace(" ", "") } // удаляем все пробелы
                            .joinToString(",")
                    } else ""

                    val previousMethod = originalExpressionText + "." + expression.method + "(" + params + ")"
                    tmpMap.getOrPut("tmp${tmpCounter++}") { previousMethod }
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


    fun getTarget(call: KtCallExpression): VariableInfo? {
        val parent = call.parent

        if (parent is KtBinaryExpression && parent.operationToken == KtTokens.EQ && parent.right == call) {
            return VariableInfo(name = parent.left?.text ?: "", type = "")
        }

        // Можно добавить destructuring, return и т.д.
        return null
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