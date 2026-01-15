package org.example.core.interfaces

import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass

interface IDependencyChainBuilder {

    fun generateChangedMethodChains(methods: List<ClassMethod>, allClasses: List<KotlinClass>): List<MethodCallNode>
    fun generateApiCallChain(callChainList: List<MethodCallNode>): List<MethodCallNode>
}
