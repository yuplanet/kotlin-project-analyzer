package org.example.core

import org.example.core.interfaces.i_projectLoader
import org.example.data.KotlinClass
import org.example.mapping.KotlinClassMapper

class DiffGraphBuilder(val projectLoader: i_projectLoader) {

    private var developClasses: List<KotlinClass> = listOf()
    private var featureClasses: List<KotlinClass> = listOf()

    public fun BuildGraph(repoPath: String, mainCommit: String, branchCommit: String){

        //1 load
        loadProject(repoPath, mainCommit, branchCommit )

        //2 proccess all call links
        collectMethodCalls(developClasses)
        collectMethodCalls(featureClasses)

        //3 filter

        //4 analizy


        //4 out
    }

    private fun loadProject(repoPath: String, mainCommit: String, branchCommit: String){

        developClasses = listOf()
        featureClasses = listOf()


        val developFiles = projectLoader.loadProjectFilesFromCommit(repoPath, mainCommit)
            .filterKeys { !it.startsWith("src/test") }

        val featureFiles = projectLoader.loadProjectFilesFromCommit(repoPath, branchCommit)
            .filterKeys { !it.startsWith("src/test") }

        val project = PsiExtractor.createProject()

        val developKtFiles = developFiles.entries.map { (name, content) ->
            PsiExtractor.createPsiFile(project, name, content)
        }

        val featureKtFiles = featureFiles.entries.map { (name, content) ->
            PsiExtractor.createPsiFile(project, name, content)
        }

        developClasses = KotlinClassMapper.mapKtFilesToClasses(developKtFiles)
        featureClasses = KotlinClassMapper.mapKtFilesToClasses(featureKtFiles)
    }

    private fun collectMethodCalls(allClasses: List<KotlinClass>) {
        CallBuilder.buildCallRecordsSimple(developClasses)
        CallBuilder.buildCallRecordsSimple(featureClasses)
    }
}

