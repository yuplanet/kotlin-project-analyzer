package org.example.data.symbol

import org.example.data.reference.MethodCallReference
import org.jetbrains.kotlin.psi.KtProperty

data class ClassProperty (
    var property: KtProperty,
    var callRecord: MutableList<MethodCallReference> = mutableListOf(),
)
