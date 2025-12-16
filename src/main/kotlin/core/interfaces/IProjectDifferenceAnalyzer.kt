package org.example.core.interfaces

import org.example.data.analyzer.ProjectDiffResult
import org.example.data.symbol.KotlinClass

interface IProjectDifferenceAnalyzer {

    fun analyzeProjectDifferences(mainProject: List<KotlinClass>, branchProject: List<KotlinClass>): ProjectDiffResult
}