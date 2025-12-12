package org.example.data

import org.jetbrains.kotlin.psi.KtNamedFunction

data class KotlinMethod(

    val Id: Int,
    val function: KtNamedFunction,

    val CallRecords: MutableList<CallMethod> = mutableListOf()
)