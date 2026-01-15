package org.example

import org.example.core.GraphBuilder

fun main() {
    try {
        val repoPath = "C:\\Users\\UlugbekYunusov\\Desktop\\a2s-reloaded"
        val mainCommit = "develop"
        val branchCommit = "feature/xxxx2"

        val diffGraphBuilder = GraphBuilder()
        diffGraphBuilder.BuildGraph(repoPath = repoPath, mainCommit = mainCommit, branchCommit = branchCommit)

    } catch (e: Exception) {
        e.printStackTrace()
    }
}