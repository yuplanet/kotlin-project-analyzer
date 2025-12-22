package org.example.data.symbol

data class FieldReference(
    override var name: String,
    override var parentClass: KotlinClass,

    var field: VariableInfo,
) : ObjectReference()