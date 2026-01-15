package org.example.core.interfaces

import org.example.data.analyzer.ProjectDiffResultOutput
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod

interface IDiffResultPresenter {
    fun writeCallChainToFile(result: ProjectDiffResultOutput)

    fun writeApiCallChainToFile(apiChain: List<MethodCallNode>, allMethods: List<ClassMethod>)
}