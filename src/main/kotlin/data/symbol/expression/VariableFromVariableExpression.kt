package org.example.data.symbol.expression


/**
 * Класс, описывающий выражения вида:
 *
 * ```
 * variable = variable
 * variable
 * ```
 */
data class VariableFromVariableExpression (
    var target: VariableInfo? = null,
    var source: VariableInfo? = null,
): BaseExpression()