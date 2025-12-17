package org.example.core

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IDiffResultPresenter
import org.example.core.interfaces.IProjectDifferenceAnalyzer
import org.example.core.interfaces.IProjectLoader
import org.example.data.analyzer.ProjectDiffResult
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.KotlinClass
import org.example.mapping.KotlinClassMapper
import java.io.File

class GraphBuilder() {

    private val projectDifferenceAnalyzer: IProjectDifferenceAnalyzer = DifferenceAnalyzer()
    private val resultPresenter: IDiffResultPresenter = DiffResultPresenter()
    private val dependencyChainBuilder = DependencyChainBuilder()
    private val callResolver: IClassReferenceBuilder = ClassReferenceBuilder()
    private val projectLoader: IProjectLoader = GitLoader()

    private var developClasses: List<KotlinClass> = listOf()
    private var featureClasses: List<KotlinClass> = listOf()

    private var diffResult = ProjectDiffResult()
    private var callChain: List<MethodCallNode> = listOf()
    private val logFile ="logs.txt"
    fun BuildGraph(repoPath: String, mainCommit: String, branchCommit: String) {

        // 1 load
        // 2 build references
        // 3 analyze difference
        // 4 generate impart chains
        // 5 output
        loadProject(repoPath, mainCommit, branchCommit)

        //2 proccess all call links
        collectMethodCalls(developClasses)
        collectMethodCalls(featureClasses)


        //4 analizy
        analyzeDifference()

        //3 filter
        generateChains()

        output()
    }

    private fun output(){
        resultPresenter.writeCallChainToFile(callChain)
    }

    private fun generateChains(){
        callChain = dependencyChainBuilder.generateChangedChains(diffResult.changedMethods,featureClasses)
    }

    private fun analyzeDifference() {
        diffResult = projectDifferenceAnalyzer.analyzeProjectDifferences(developClasses, featureClasses)
    }

    private fun collectMethodCalls(projectClasses: List<KotlinClass>) {

        logStatus(logFile, "The project binding started")

        try {
            callResolver.bindAll(projectClasses)
            logStatus(logFile, "The project binding success")
        } catch (ex: Exception) {
            logStatus(logFile, "The project binding error ${ex.message}")
            throw ex
        }
    }

    private fun loadProject(repoPath: String, mainCommit: String, branchCommit: String) {

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


    private fun logStatus(filename: String, content: String) {
        File(filename).appendText(content + System.lineSeparator())
    }
}