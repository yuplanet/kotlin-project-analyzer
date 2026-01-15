package org.example.core.interfaces

import org.example.data.analyzer.ProjectDiffResultOutput
import org.example.data.chain.MethodCallNode

interface IDiffResultPresenter {
    fun writeCallChainToFile(result: ProjectDiffResultOutput)

    fun writeApiCallChainToFile(apiChain: List<MethodCallNode>)
}