package org.example.data

import org.jetbrains.kotlin.psi.KtParameter

data class ClassProperty (
    var property: KtParameter,
    var callRecord: MutableList<CallMethod> = mutableListOf(),
    var reverseCallRecords: MutableList<CallMethod> = mutableListOf()
)
