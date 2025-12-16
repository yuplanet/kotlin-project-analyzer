package org.example

import org.example.core.GraphBuilder
import org.example.core.interfaces.IProjectLoader
import org.example.plugins.GitLoader

fun main() {
    try {
        val repoPath = "C:\\Users\\UlugbekYunusov\\Desktop\\a2s-reloaded"
        val mainCommit = "develop"
        val branchCommit = "feature/xxxx"


        val projectLoader: IProjectLoader = GitLoader()
        val diffGraphBuilder = GraphBuilder(projectLoader)
        diffGraphBuilder.BuildGraph(repoPath = repoPath, mainCommit = mainCommit, branchCommit = branchCommit)

    } catch (e: Exception) {
        e.printStackTrace()
    }
}