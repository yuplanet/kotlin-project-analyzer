package org.example.data.symbol

import org.example.data.symbol.expression.MethodValue

data class MethodReference(
    override var name: String,
    override var parentClass: KotlinClass,

    override var signature: String, // по нему можем найти метод

    val method: MethodValue,
) : ObjectReference()