package org.example.data.chain

import org.jetbrains.kotlin.psi.KtNamedFunction

data class MethodCallNode (
    /**
     * <путь_к_файлу>.<имя_класса>::<имя_метода> ( параметры )
     */
    val fullName: String,
    val function: KtNamedFunction,
    var updates: String,

    val calls: MutableList<MethodCallNode>  = mutableListOf(),
    val reverseCalls: MutableList<MethodCallNode>  = mutableListOf()
)