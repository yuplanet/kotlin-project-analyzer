package org.example.data.symbol.expression


/**
 * Класс, описывающий выражения вида:
 *
 * ```
 * Class.Field = Class.Field
 * Class.Field
 * ```
 */

data class FieldFromFieldExpression (
    var target: FieldInfo? = null,
    val source: FieldInfo = FieldInfo(),
): BaseExpression()