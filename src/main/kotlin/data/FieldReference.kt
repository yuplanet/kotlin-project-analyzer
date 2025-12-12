package org.example.data

import org.jetbrains.kotlin.psi.KtProperty

data class FieldReference (
    var property: KtProperty,
    var callRecord: MutableList<CallMethod> = mutableListOf()
)
