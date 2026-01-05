package org.example.data.symbol

import org.example.data.symbol.expression.MethodValue

data class MethodReference(
    override var name: String,
    override var parentClass: KotlinClass,

    val method: MethodValue, // по нему можем найти метод
) : ObjectReference()