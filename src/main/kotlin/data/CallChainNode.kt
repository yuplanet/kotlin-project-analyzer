package org.example.data

import org.jetbrains.kotlin.psi.KtNamedFunction

data class CallChainNode (
    /**
     * <путь_к_файлу>.<имя_класса>::<имя_метода> ( параметры )
     */
    val fullName: String,
    val function: KtNamedFunction,
    val updates: String,

    val nextCalls: MutableList<CallChainNode>  = mutableListOf()
)
