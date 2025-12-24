package org.example.data.symbol.expression


/**
 * Класс, описывающий выражения вида:
 *
 * ```
 * Class.Field = Class.Method()
 * ```
 */

data class FieldFromMethodExpression (
    var target: FieldInfo? = null,
    val receiver: VariableInfo? = null,
    val method: MethodInfo = MethodInfo(),
): BaseExpression()