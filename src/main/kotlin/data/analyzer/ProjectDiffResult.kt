package org.example.data.analyzer

import org.example.data.symbol.ClassMethod

data class ProjectDiffResult(
    val added: List<ClassMethod>,
    val removed: List<ClassMethod>,
    val changed: List<ClassMethod>
)