package org.example.data

import org.jetbrains.kotlin.psi.KtParameter

data class ParamReference (
    var property: KtParameter,
    var callRecord: MutableList<CallMethod> = mutableListOf()
)
