package org.example.core.interfaces

interface i_projectLoader {

    fun loadProjectFilesFromCommit(repoPath: String, commit: String): Map<String, String>
}