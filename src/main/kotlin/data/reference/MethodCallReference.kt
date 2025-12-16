package org.example.data.reference

import org.example.data.symbol.KotlinClass

data class MethodCallReference(
    val fullName: String,
    val parentClass: KotlinClass
)