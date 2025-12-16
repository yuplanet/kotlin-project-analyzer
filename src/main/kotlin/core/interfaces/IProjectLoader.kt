package org.example.core.interfaces

interface IProjectLoader {

    fun loadProjectFilesFromCommit(repoPath: String, commit: String): Map<String, String>
}