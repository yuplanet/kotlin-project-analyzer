package org.example.data.symbol

import org.example.data.symbol.expression.ExpressionValue

data class FieldReference(
    override var referenceTargetName: String,
    override var referenceTargetParentClass: KotlinClass,

    override var signature: String,

    var expressionValue: ExpressionValue?,
) : ObjectReference()