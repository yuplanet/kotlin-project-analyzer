package org.example.core.interfaces

import org.example.data.analyzer.ProjectDiffResultOutput

interface IDiffResultPresenter {
    fun writeCallChainToFile(result: ProjectDiffResultOutput): String
}