package org.example.core.psi

import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.linking.ExpressionTypeResolver
import org.example.data.symbol.*
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*

class KtFunctionExpressionCollector {

    val tmpMap = mutableMapOf<String, String>()
    var tmpCounter = 1

    private lateinit var currentClassMethod: ClassMethod
    private lateinit var searchEngine: IProjectSearchEngine
    private lateinit var mainClass: KotlinClass
    private lateinit var typeResolver: ExpressionTypeResolver

    private fun buildFullExpression(
        currentExpression: KtCallExpression,
        callingContext: VariableInfo
    ): FullExpression {

        val receiverExpression = (currentExpression.parent as? KtDotQualifiedExpression)?.receiverExpression
            ?: (currentExpression.parent as? KtSafeQualifiedExpression)?.receiverExpression

        val receiverName = receiverExpression?.text?.let { tmpMap[it] }
            ?: when (receiverExpression) {
                is KtNameReferenceExpression -> receiverExpression.getReferencedName()
                is KtThisExpression -> "this"
                else ->  tmpMap.entries.firstOrNull { it.value == receiverExpression?.text }?.key ?: receiverExpression?.text
                    ?: "this" // сюда не должно попасть сложное выражение, если tmpMap работает
            }

        val methodName = currentExpression.calleeExpression?.text ?: "" // мя метода

        val params = currentExpression.valueArguments.map { arg ->
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

            val expressionsChain = currentClassMethod.fullExpressions

            // Пытаемся найти тип в уже известных FullExpression
            val paramType = expressionsChain
                .firstOrNull { it.target.name == paramName }?.target?.type
                ?: expressionsChain.firstOrNull { expr -> expr.method.parameters.any { it.name == paramName } }
                    ?.method.parameters?.firstOrNull { it.name == paramName }?.type
                ?: ""

            VariableInfo(paramName ?: "", paramType)
        }

        val returnType = ""

        val expression = FullExpression(
            target = callingContext,
            receiver = receiverName,
            method = methodName,
            methodReturnType = "_",
            params = params
        )

        return expression
    }


    //psi element - это все что угодно!!!
    private fun handlePsiElement(currentElement: PsiElement, callingContext: VariableInfo?=null) {

        if (currentElement is KtProperty) {

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


        else if (currentElement is KtParameter) {
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
        else  if (currentElement is KtCallExpression) {
            // Сначала обрабатываем аргументы рекурсивно
            currentElement.valueArguments.forEach { arg ->
                arg.getArgumentExpression()?.let { handlePsiElement(it) }
            }

            val receiverExpression = (currentElement.parent as? KtDotQualifiedExpression)?.receiverExpression
                ?: (currentElement.parent as? KtSafeQualifiedExpression)?.receiverExpression

            val receiverText = receiverExpression?.text ?: "this" // имя serivce
            val callWithoutContext = callingContext?.name.isNullOrEmpty()

// Для сложных выражений, включающих вызовы, лучше проверять:

            if (callWithoutContext) {
                val isComplex =
                    receiverExpression is KtCallExpression || receiverExpression is KtDotQualifiedExpression || receiverExpression is KtSafeQualifiedExpression

                if (isComplex){

                    val value = "tmp${tmpCounter++}"
                    tmpMap.getOrPut(value) { receiverText }
                }
            }

            var currentContext = if (callingContext == null) {
                VariableInfo(

                    tmpMap.entries.firstOrNull { it.value == receiverText }?.key ?: receiverText,
                    ""
                )
            } else
                callingContext


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
            typeResolver.resolveExpressionType(expression, currentClassMethod, mainClass)



            val methReturnType = searchEngine.findMethodByClassNameAndMethodNameAndParams(expression.receiver.type, expression.method.name,
                expression.method.parameters.map { it.type })
            expression.method.returnType = methReturnType?.returnType?:"_"
        }

        ///dot
    else  if (currentElement is KtDotQualifiedExpression) {
            // Receiver становится parent для selector
            currentElement.receiverExpression?.let { handlePsiElement(it, callingContext) }
            currentElement.selectorExpression?.let { handlePsiElement(it, callingContext) }
        }
    else  if (currentElement is KtSafeQualifiedExpression) {
            currentElement.receiverExpression?.let { handlePsiElement(it, callingContext) }
            currentElement.selectorExpression?.let { handlePsiElement(it, callingContext) }
        }


        ///others

    else  if (currentElement is KtBlockExpression)
            currentElement.statements.forEach { handlePsiElement(it, callingContext) }

    else if (currentElement is KtForExpression)
            currentElement.body?.let { handlePsiElement(it, callingContext) }

    else if (currentElement is KtWhileExpression)
            currentElement.body?.let { handlePsiElement(it, callingContext) }


    else  if (currentElement is KtIfExpression) {
            currentElement.then?.let { handlePsiElement(it, callingContext) }
            currentElement.`else`?.let { handlePsiElement(it, callingContext) }
        }

    else  if (currentElement is KtTryExpression) {
            handlePsiElement(currentElement.tryBlock, callingContext)
            currentElement.catchClauses.forEach { it.catchBody?.let { handlePsiElement(it, callingContext) } }
            currentElement.finallyBlock?.finalExpression?.let { handlePsiElement(it, callingContext) }
        } else
            currentElement.children.forEach { handlePsiElement(it, callingContext) }
    }


    fun collectExpressions(method: ClassMethod, kotlinClass: KotlinClass, searchEngine: IProjectSearchEngine) {

        currentClassMethod = method
        this.searchEngine = searchEngine
        this.mainClass = kotlinClass

        typeResolver = ExpressionTypeResolver(searchEngine)
        val currentMethodFnNamedFunction = method.function

        currentMethodFnNamedFunction.bodyExpression?.let { handlePsiElement(it) } // it: KtExpression
    }
}