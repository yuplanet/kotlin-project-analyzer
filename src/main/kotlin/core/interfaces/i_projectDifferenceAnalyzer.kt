package org.example.core.interfaces

import org.example.data.DiffResult
import org.example.data.KotlinClass

interface i_projectDifferenceAnalyzer {

    fun analyzeProjectDifferences(mainProject: List<KotlinClass>, branchProject: List<KotlinClass>): DiffResult
}