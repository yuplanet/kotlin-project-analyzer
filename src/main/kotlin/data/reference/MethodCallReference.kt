package org.example.data.reference

import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodInfo

data class MethodCallReference(
    val fullName: String,
    val method: MethodInfo,
    val parentClass: KotlinClass
)