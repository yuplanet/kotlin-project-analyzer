package org.example.data.symbol

data class MethodReference(
    override var name: String,
    override var parentClass: KotlinClass,

    val method: MethodInfo, // по нему можем найти метод
) : ObjectReference()