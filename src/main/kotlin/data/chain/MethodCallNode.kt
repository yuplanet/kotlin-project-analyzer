package org.example.data.chain

import org.example.data.symbol.ClassMethod
import org.jetbrains.kotlin.psi.KtNamedFunction

class MethodCallNode(
    val fullName: String,
    val function: KtNamedFunction,
    var updates: String,
    var method: ClassMethod

) {


    var visitedFunctionHistory: MutableList<String> = mutableListOf()
    val calls: MutableList<MethodCallNode> = mutableListOf()

    var visitedReverseFunctionHistory: MutableList<String> = mutableListOf()
    val reverseCalls: MutableList<MethodCallNode> = mutableListOf()

    override fun toString(): String {
        // безопасный вывод, без рекурсии
        return fullName + if (updates.isNotBlank()) " ($updates)" else ""
    }
}