package org.example.data.symbol

import org.example.data.reference.ObjectReference
import org.jetbrains.kotlin.psi.KtParameter

data class ClassParameter (

    var name: String,
    var type: String,
    var property: KtParameter,
    var field: VariableInfo,

    var callRecords: MutableList<ObjectReference> = mutableListOf(),
    var reverseCallRecords: MutableList<ObjectReference> = mutableListOf(),
)