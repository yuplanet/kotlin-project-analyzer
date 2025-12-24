package org.example.data.symbol

import org.example.data.symbol.expression.VariableInfo

data class FieldReference(
    override var name: String,
    override var parentClass: KotlinClass,

    var field: VariableInfo,
) : ObjectReference()