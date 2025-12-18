package org.example.data.symbol

import org.example.data.reference.MethodCallReference
import org.jetbrains.kotlin.psi.KtParameter

data class ClassParameter (

    var name: String,
    var type: String,
    var property: KtParameter,

    var callRecord: MutableList<MethodCallReference> = mutableListOf(),
)
