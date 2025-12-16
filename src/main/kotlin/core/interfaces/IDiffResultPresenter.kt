package org.example.core.interfaces

import org.example.data.chain.MethodCallNode

interface IDiffResultPresenter {
    fun writeCallChainToFile(result: List<MethodCallNode>)
}