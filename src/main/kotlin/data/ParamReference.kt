package org.example.data

import org.jetbrains.kotlin.psi.KtProperty

data class ParamReference (
    var Id: Int,
    var Property: KtProperty,

    var CallRecord: MutableList<CallMethod> = mutableListOf()
)
