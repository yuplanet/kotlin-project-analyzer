package org.example.core.interfaces

import org.example.data.CallChainNode
import org.example.data.KotlinClass
import org.example.data.KotlinMethod

interface i_dependencyChainBuilder {
    fun generateChain(
        rootMethod: KotlinMethod,
        allClasses: List<KotlinClass>
    ): CallChainNode


    fun generateChangedChains(
    methods: List<KotlinMethod>,
    allClasses: List<KotlinClass>,
    ): List<CallChainNode>
}