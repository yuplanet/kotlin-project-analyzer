package org.example

import org.example.core.GraphBuilder

fun main() {
    try {
        val repoPath = "repo_path"
        val mainCommit = "develop"
        val branchCommit = "feature/xxxx4"

        val diffGraphBuilder = GraphBuilder()
        diffGraphBuilder.BuildGraph(repoPath = repoPath, mainCommit = mainCommit, branchCommit = branchCommit)

    } catch (e: Exception) {
        e.printStackTrace()
    }
}