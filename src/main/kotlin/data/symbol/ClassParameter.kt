package org.example.data.symbol

import org.example.data.reference.MethodCallReference
import org.jetbrains.kotlin.psi.KtParameter

data class ClassParameter (
    var property: KtParameter,
    var callRecord: MutableList<MethodCallReference> = mutableListOf(),
    var reverseCallRecords: MutableList<MethodCallReference> = mutableListOf()
)
