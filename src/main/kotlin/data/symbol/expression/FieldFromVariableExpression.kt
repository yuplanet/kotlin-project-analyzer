package org.example.data.symbol.expression

/**
 * Класс, описывающий выражения вида:
 *
 * ```
 * Class.Field = variable
 * variable
 * ```
 */

data class FieldFromVariableExpression(
    var target: FieldInfo? = null,
    val source: VariableInfo
): BaseExpression()