package org.example.core.interfaces

import org.example.data.chain.MethodCallNode
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.ClassMethod

interface IDependencyChainBuilder {

    fun generateChangedMethodChains(methods: List<ClassMethod>, allClasses: List<KotlinClass>): List<MethodCallNode>

    fun generateAddedMethodChains(methods: List<ClassMethod>, allClasses: List<KotlinClass>): List<MethodCallNode>

    fun generateRemovedMethodChains(methods: List<ClassMethod>, allClasses: List<KotlinClass>): List<MethodCallNode>
}
