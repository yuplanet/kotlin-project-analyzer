package org.example.data.analyzer

import org.example.data.chain.MethodCallNode

data class ProjectDiffResultOutput (
    var changedMethods: List<MethodCallNode> = mutableListOf(),
    var addedMethods: List<MethodCallNode> = mutableListOf(),
    var removedMethods: List<MethodCallNode> = mutableListOf(),
)