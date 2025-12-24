package org.example.data.symbol.expression

/**
 * Класс, описывающий выражения вида:
 *
 * ```
 * variable = Class.Field
 * Class.Field
 * ```
 */
data class VariableFromFieldExpression(
    val target: VariableInfo? = null,
    val source: FieldInfo
): BaseExpression()