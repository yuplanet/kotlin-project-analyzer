package org.example.data.symbol.expression


/**
 * Класс, описывающий выражения вида:
 *
 * ```
 * variable = Class.Method()
 * variable = Method()
 * Method()
 *
 * ```
 */

data class VariableFromMethodExpression (
    var target: VariableInfo? = null,
    val receiver: VariableInfo? = null,
    val method: MethodInfo = MethodInfo(),
): BaseExpression()