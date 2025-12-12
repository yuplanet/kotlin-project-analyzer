package org.example.plugins

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File

object GitLoader {
    fun loadFilesFromCommit(repoPath: String, commit: String): Map<String, String> {
        val builder = FileRepositoryBuilder()
        val repo: Repository = builder.setGitDir(File("$repoPath/.git"))
            .readEnvironment()
            .findGitDir()
            .build()

        val commitId = repo.resolve(commit)
        val commitObj = repo.parseCommit(commitId)
        val tree = commitObj.tree

        val treeWalk = TreeWalk(repo)
        treeWalk.addTree(tree)
        treeWalk.isRecursive = true

        val files = mutableMapOf<String, String>()
        while (treeWalk.next()) {
            val path = treeWalk.pathString
            if (path.endsWith(".kt")) {
                val loader = repo.open(treeWalk.getObjectId(0))
                files[path] = String(loader.bytes)
            }
        }
        return files
    }


}