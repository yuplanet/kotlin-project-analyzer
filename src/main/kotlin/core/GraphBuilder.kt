package org.example.core

import org.example.core.interfaces.i_dependencyChainBuilder
import org.example.core.interfaces.i_diffResultPresenter
import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.i_projectDifferenceAnalyzer
import org.example.core.interfaces.i_projectLoader
import org.example.data.CallChainNode
import org.example.data.DiffResult
import org.example.data.KotlinClass
import org.example.mapping.KotlinClassMapper

class GraphBuilder(val projectLoader: i_projectLoader,) {

    private val projectDifferenceAnalyzer: i_projectDifferenceAnalyzer = DifferenceAnalyzer()
    private val resultPresenter: i_diffResultPresenter = DiffResultPresenter()
    private val dependencyChainBuilder: i_dependencyChainBuilder = DependencyChainBuilder()
    private val callResolver: IClassReferenceBuilder = ClassReferenceBuilder()

    private var developClasses: List<KotlinClass> = listOf()
    private var featureClasses: List<KotlinClass> = listOf()

    private var diffResult = DiffResult(listOf(), listOf(), listOf())
    private var callChain: List<CallChainNode> = listOf()

    fun BuildGraph(repoPath: String, mainCommit: String, branchCommit: String) {

        // 1 load
        // 2 build references
        // 3 analyze difference
        // 4 generate impart chains
        // 5 output

        //clear inside state
        clearState()

        //1 load
        loadProject(repoPath, mainCommit, branchCommit)

        //2 proccess all call links
        collectMethodCalls(developClasses)
        collectMethodCalls(featureClasses)
        //4 analizy

        analyzeDifference()
        //3 filter
        generateChains()

        //4 out
        OutputResult()
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

    private fun collectMethodCalls(allClasses: List<KotlinClass>) {

        callResolver.initialize(allClasses)
        callResolver.bindAll()
    }

    private fun analyzeDifference() {
        diffResult = projectDifferenceAnalyzer.analyzeProjectDifferences(developClasses, featureClasses)
    }

    private fun OutputResult(){
        resultPresenter.writeCallChainToFile(callChain)
    }


    private fun generateChains(){
        callChain = dependencyChainBuilder.generateChangedChains(diffResult.changed,featureClasses)
    }

    private fun clearState(){

    }
}

