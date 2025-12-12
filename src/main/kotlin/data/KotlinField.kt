package org.example.data

import org.jetbrains.kotlin.psi.KtProperty

data class KotlinField (

    val Id: Int,
    val Field: KtProperty,

    val ParamRecords: MutableList<CallMethod> = mutableListOf()
)