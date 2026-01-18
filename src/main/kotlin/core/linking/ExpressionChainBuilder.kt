package org.example.core.linking

import org.example.core.interfaces.IExpressionTypeResolver
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.interfaces.ITemporaryVariableStorage
import org.example.core.psi.KtExpressionInspector
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.expression.*
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class ExpressionChainBuilder(
    private val searchEngine: IProjectSearchEngine
) {
    private lateinit var currentMethod: ClassMethod
    private lateinit var mainClass: KotlinClass
    private lateinit var typeResolver: IExpressionTypeResolver
    private val variableStorage: ITemporaryVariableStorage = ExpressionValueStorage()

    val unknownType = "unknown"

    fun collectAllExpressions(method: ClassMethod) {

        //init dependencies
        typeResolver = ExpressionTypeResolver(searchEngine, method)
        mainClass = method.parentClass
        currentMethod = method

        val block = method.function.bodyBlockExpression ?: return

        // резолвим сначала параметры
        for (param in method.function.valueParameters) {
            handlePsiElement(param)
        }

        //потом тело
        for (statement in block.statements) {
            handlePsiElement(statement)
        }
    }

    // основные элементы на которые
    fun handlePsiElement(
        element: PsiElement,
        context: ExpressionValue? = null,
        hasTarget: Boolean = false
    ): ExpressionValue? {

        element.text

        ////////////////////////////////////////////////////////////////////////////////empty
        val target: ExpressionValue? = when (element) {

            is KtIfExpression -> {
                val thenResult = element.then?.let { handlePsiElement(it, context, hasTarget) }
                val elseResult = element.`else`?.let { handlePsiElement(it, context, hasTarget) }

                // если мы в контексте выражения — вернём результат
                thenResult ?: elseResult
            }

            is KtBlockExpression -> {
                var last: ExpressionValue? = null
                element.statements.forEach { stmt ->
                    last = handlePsiElement(stmt, context, hasTarget) ?: last
                }
                last
            }

            is KtWhenExpression -> {
                element.subjectExpression?.let {
                    handlePsiElement(it, context, hasTarget)
                }

                element.entries.forEach { entry ->
                    entry.conditions.forEach { cond ->
                        handlePsiElement(cond, context, hasTarget)
                    }
                    entry.expression?.let {
                        handlePsiElement(it, context, hasTarget)
                    }
                }
                null
            }

            is KtForExpression, is KtWhileExpression, is KtDoWhileExpression -> {
                element.body?.let { handlePsiElement(it, context, hasTarget) }
                null
            }

            is KtTryExpression -> {
                // результат из try
                val tryResult = handlePsiElement(element.tryBlock, context, hasTarget)

                // иначе обходим catch
                element.catchClauses.forEach { clause ->
                    val catchResult = clause.catchBody?.let {
                        handlePsiElement(it, context, hasTarget)
                    }

                    if (catchResult != null) {
                        return catchResult
                    }
                }

                // finally просто обходим (тип не влияет)
                element.finallyBlock?.finalExpression?.let {
                    handlePsiElement(it, context, hasTarget)
                }

                // если вообще ничего не извлекли — считаем Unit
                tryResult
            }

            is KtLambdaExpression -> {
                val body = element.bodyExpression ?: return null
                var result: ExpressionValue? = null

                body.statements.forEach { stmt ->
                    result = handlePsiElement(stmt, context, hasTarget)
                }
                result
            }


            is KtIsExpression -> {
                element.leftHandSide?.let {
                    handlePsiElement(it, context, hasTarget)
                }
                null
            }

            is KtArrayAccessExpression -> {
                element.arrayExpression?.let {
                    handlePsiElement(it, context, hasTarget)
                }
                element.indexExpressions.forEach {
                    handlePsiElement(it, context, hasTarget)
                }
                null
            }

            ///////////////////////////////////////////////////////////

            is KtProperty -> {

                val initializer = element.initializer

                val source = initializer?.let {

                    initializer.text
                    handlePsiElement(it, hasTarget = true) // target  - ktProperty
                }

                val name = element.name ?: "unknown"
                val type = element.typeReference?.text ?: unknownType

                val target = VariableValue(name, type)

                source?.let {
                    normalizeTypes(target, source)
                }

                val property = ClassProperty(
                    name = name,
                    property = element,
                    type = type,
                    parentClass = mainClass)

                currentMethod.properties.add(property)

                addExpression(target, source,)
                return target
            }

            is KtParameter -> {
                val initializer = element.defaultValue

                val source: ExpressionValue? = initializer?.let {

                    initializer.text
                    handlePsiElement(it, hasTarget = true)
                }

                val name = element.name ?: "unknown"
                val type = element.typeReference?.text ?: unknownType

                val target = VariableValue(name, type)

                source?.let {
                    normalizeTypes(target, source)
                }

                val parameter = ClassParameter(
                    name = target.variableName,
                    property = element,
                    type = target.variableType,
                    parentClass = mainClass)

                currentMethod.parameters.add(parameter)
                addExpression(target, source,)
                return target
            }

            is KtNameReferenceExpression -> {
                // parent не нужонный

                val variableName = element.getReferencedName()

                element.text

                var type = if (context != null && context is VariableValue)
                    typeResolver.getVariableTypeByNameAndClass(context.variableType, variableName)
                else
                    typeResolver.getVariableTypeByName(variableName)

                if (type == null) {
                    val objClass = searchEngine.findByClassName(variableName)
                    type = objClass?.name ?: unknownType
                }

                var value = if (context != null && context is VariableValue) {
                    FieldValue(
                        fieldName = variableName,
                        fieldType = type,
                        qualifier = context.variableName,
                        qualifierType = context.variableType
                    )
                } else {
                    VariableValue(variableName, type)
                }
                return value
            }

            is KtCallExpression -> {

                element.text
                val calleeName = element.calleeExpression?.text
                val loopLikeFunctions = setOf("forEach", "map", "flatMap", "onEach", "filter")

                if (calleeName in loopLikeFunctions) {
                    // коллекция, на которой вызывается forEach
                    (element.parent as? KtDotQualifiedExpression)
                        ?.receiverExpression
                        ?.let { handlePsiElement(it, context, hasTarget) }

                    // лямбда-аргумент
                    element.lambdaArguments.firstOrNull()
                        ?.getLambdaExpression()
                        ?.bodyExpression
                        ?.statements
                        ?.forEach { stmt ->
                            handlePsiElement(stmt, context, hasTarget)
                        }

                    null
                } else {

                    val receiver = context?.let { getReceiveVariable(it) }

                    val method = completeMethod(element, receiver)

                    if (hasTarget)
                        return method

                    val tmpName = variableStorage.add(method)

                    val target = VariableValue(tmpName, method.methodReturnType)

                    addExpression(target, method)

                    target
                }
            }

            is KtDotQualifiedExpression,
            is KtSafeQualifiedExpression,
                -> {

                val dotExpression = element as KtQualifiedExpression
                val leftExpression: KtExpression = dotExpression.receiverExpression
                val rightExpression = dotExpression.selectorExpression

                element.text
                leftExpression.text
                rightExpression?.text

                /// left . right
                var target: ExpressionValue? = handlePsiElement(leftExpression)

                if (target is VariableValue && target.variableType == unknownType && rightExpression?.text != null) {
                    val type = typeResolver.getVariableTypeByNameAndClass(leftExpression.text, rightExpression.text)
                    target = VariableValue(rightExpression.text, type ?: unknownType)
                }

                val source: ExpressionValue? = rightExpression?.let {
                    handlePsiElement(it, target, hasTarget = hasTarget)
                }

                if (hasTarget)
                    return source

                if (source == null)
                    return target

                val tmpName = variableStorage.add(source)

                val tempTarget = VariableValue(tmpName, getExpressionValueType(source))

                normalizeTypes(tempTarget, source)
                addExpression(tempTarget, source)

                tempTarget
            }

            is KtBinaryExpression -> {

                val op = element.operationToken

                // 1) Elvis operator ?:
                if (op == KtTokens.ELVIS) {

                    element.left?.let { handlePsiElement(it, context, hasTarget) }

                    element.right?.let { handlePsiElement(it, context, hasTarget) }

                    return null
                } else if (op != KtTokens.EQ)
                    return null

                val leftExpression = element.left ?: error("Left-hand side missing ${element.left?.text}")
                val rightExpression = element.right ?: error("Right-hand side missing ${element.right?.text}")

                element.text
                element.left?.text
                element.right?.text

                // 1️⃣ обрабатываем правую часть (источник)
                val source = handlePsiElement(rightExpression, null, hasTarget = true)

                // 2️⃣ обрабатываем левую часть (цель)
                var target = handlePsiElement(leftExpression, null, hasTarget = true)

                if (target == null && source != null) {
                    val tmpName = variableStorage.add(source)
                    target = VariableValue(tmpName, getExpressionValueType(source))
                } else if (source == null)
                    return null

                normalizeTypes(target!!, source)
                addExpression(target, source)
                target
            }

            is KtUnaryExpression -> {
                // например: !a, -b, ++i, --i
                element.baseExpression?.let {
                    handlePsiElement(it, context, hasTarget)
                }
                null
            }

            is KtReturnExpression -> {
                element.returnedExpression?.let {
                    handlePsiElement(it, context, hasTarget)
                }
            }

            is KtThrowExpression -> {
                element.thrownExpression?.let {
                    handlePsiElement(it, context, hasTarget)
                }
                null
            }

            else -> null//handlePsiElement(element, context, hasTarget)
        }

        return target
    }


    /////////////////////////////// Process chapter
    fun completeMethod(expression: KtCallExpression, receiver: VariableValue?): MethodValue {

        //Metho
        val methodName = expression.calleeExpression?.text ?: "" // мя метода

        val params: MutableList<ExpressionValue> = mutableListOf()

        for (arg in expression.valueArguments) {

            val expression = arg.getArgumentExpression()
            arg.text

            expression?.let {
                val param = handlePsiElement(it, null)

                param?.let {
                    params.add(it)
                }
            }
        }

        val method = MethodValue(
            methodName = methodName,
            parameters = params.toMutableList(),
            methodReturnType = unknownType
        )
        method.rawValue = KtExpressionInspector.getRawCallText(expression)

        if (receiver != null) {

            //
            method.receiverName = receiver.variableName
            method.receiverClassName = receiver.variableType
            method.innerCall = false
        } else {
            method.receiverName = mainClass.name
            method.receiverClassName = mainClass.name
            method.innerCall = true
        }

        val methodType = typeResolver.getMethodReturnTypeByNameReceiveAndParamTypes(
            methodName = methodName,
            receiverClass = receiver?.variableType ?: mainClass.name,
            params.map { getExpressionValueType(it) }
        ) ?: unknownType

        method.methodReturnType = methodType

        if (methodType == unknownType)
            method.methodReturnType = method.receiverClassName
        return method
    }

    /////// Value Chapter

    //принимает xpressionValue И дает receive в виде Variable
    fun getReceiveVariable(expr: ExpressionValue): VariableValue? {

        if (expr is VariableValue) { // дальше этого не зайдет
            return expr
        } else if (expr is FieldValue) {
            return VariableValue(expr.fieldName, expr.fieldType)
        } else if (expr is MethodValue) {
            return VariableValue(expr.methodName, expr.methodReturnType)
        }
        return null
    }

    private fun normalizeTypes(target: ExpressionValue, source: ExpressionValue) {

        fun fixTypes(target: ExpressionValue, sourceType: String) {
            when (target) {
                is VariableValue -> target.variableType = sourceType
                is FieldValue -> target.fieldType = sourceType
                is MethodValue -> target.methodReturnType = sourceType
            }
        }

        val targetType = getExpressionValueType(target)
        val sourceType = getExpressionValueType(source)

        if (hasValidType(targetType) && !hasValidType(sourceType)) {
            fixTypes(source, targetType)
        } else if (hasValidType(sourceType) && !hasValidType(targetType)) {
            fixTypes(target, sourceType)
        }
    }

    private fun getExpressionValueType(value: ExpressionValue): String {

        if (value is VariableValue)
            return value.variableType
        else if (value is FieldValue)
            return value.fieldType
        else if (value is MethodValue)
            return value.methodReturnType
        return unknownType
    }

    private fun hasValidType(type: String): Boolean {
        return type != unknownType && type.isNotEmpty()
    }

    ///////// others
    private fun addExpression(
        target: ExpressionValue?,
        source: ExpressionValue?
    ) {

        var operation = AssignmentExpression(
            target = target,
            source = source
        )

        currentMethod.fullExpressions.add(operation)
    }
}