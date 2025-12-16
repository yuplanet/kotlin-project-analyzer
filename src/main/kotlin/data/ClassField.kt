package org.example.data

import org.jetbrains.kotlin.psi.KtProperty

data class ClassField (
    var property: KtProperty,
    var callRecord: MutableList<CallMethod> = mutableListOf(),
    var reverseCallRecords: MutableList<CallMethod> = mutableListOf()
)
