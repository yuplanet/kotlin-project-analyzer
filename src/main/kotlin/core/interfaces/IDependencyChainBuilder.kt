package org.example.core.interfaces

import org.example.data.chain.MethodCallNode
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.ClassMethod

interface IDependencyChainBuilder {
    fun generateChain(rootMethod: ClassMethod, allClasses: List<KotlinClass>): MethodCallNode

    fun generateChangedChains(methods: List<ClassMethod>, allClasses: List<KotlinClass>): List<MethodCallNode>

}
