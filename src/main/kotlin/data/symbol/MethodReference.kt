package org.example.data.symbol

import org.example.data.symbol.expression.MethodInfo

data class MethodReference(
    override var name: String,
    override var parentClass: KotlinClass,

    val method: MethodInfo, // по нему можем найти метод
) : ObjectReference()