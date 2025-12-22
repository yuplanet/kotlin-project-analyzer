package org.example.data.symbol

import org.example.data.reference.ObjectReference
import org.jetbrains.kotlin.psi.KtProperty

data class ClassProperty (

    var name: String,
    var type: String,
    var property: KtProperty,
    var field: VariableInfo,

    var callRecords: MutableList<ObjectReference> = mutableListOf(),
    var reverseCallRecords: MutableList<ObjectReference> = mutableListOf(),
)
