package org.example.data.symbol

import org.example.data.symbol.expression.ExpressionValue

data class FieldReference(
    override var name: String,
    override var parentClass: KotlinClass,

    override var signature: String,

    var expressionValue: ExpressionValue?,
) : ObjectReference()