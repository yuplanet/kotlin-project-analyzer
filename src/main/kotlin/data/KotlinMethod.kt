package org.example.data

import org.jetbrains.kotlin.psi.KtNamedFunction

data class KotlinMethod(

    /**
     * Id Метода
     */
    val Id: Int,
    val Function: KtNamedFunction,

    val CallRecords: MutableList<CallMethod> = mutableListOf()
)