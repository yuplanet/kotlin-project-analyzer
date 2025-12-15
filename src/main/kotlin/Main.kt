package org.example

import org.example.core.GraphBuilder
import org.example.core.interfaces.i_projectLoader
import org.example.plugins.GitLoader

fun main() {
    try {
        val repoPath = "C:\\Users\\UlugbekYunusov\\Desktop\\a2s-reloaded"
        val mainCommit = "develop"
        val branchCommit = "feature/xxxx"


        val projectLoader: i_projectLoader = GitLoader()
        val diffGraphBuilder = GraphBuilder(projectLoader)
        diffGraphBuilder.BuildGraph(repoPath = repoPath, mainCommit = mainCommit, branchCommit = branchCommit)

    } catch (e: Exception) {
        e.printStackTrace()
    }
}