package org.example.core

import org.example.core.interfaces.*
import org.example.core.linking.ClassReferenceBuilder
import org.example.core.linking.DependencyChainBuilder
import org.example.core.psi.KtFileExtractor
import org.example.core.search.SearcherEngine
import org.example.core.utils.KtFileMapper
import org.example.data.analyzer.ProjectDiffResult
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.KotlinClass
import java.io.File

class GraphBuilder() {

    //modules
    private val differenceAnalyzer: IProjectDifferenceAnalyzer = DifferenceAnalyzer()
    private val resultPresenter: IDiffResultPresenter = DiffResultPresenter()
    private val dependencyChainBuilder: IDependencyChainBuilder = DependencyChainBuilder()
    private val referenceBuilder: IClassReferenceBuilder = ClassReferenceBuilder()
    private val projectLoader: IProjectLoader = GitLoader()


    //state
    private var developClasses: List<KotlinClass> = listOf()
    private var featureClasses: List<KotlinClass> = listOf()

    //separate search engine
    private lateinit var devSearchEngine: IProjectSearchEngine
    private lateinit var featSearchEngine: IProjectSearchEngine

    //output
    private var diffResult = ProjectDiffResult()
    private var callChain: List<MethodCallNode> = listOf()


    //logs
    private val logFile ="logs.txt"


    fun BuildGraph(repoPath: String, mainCommit: String, branchCommit: String) {

        // 1 load project
        // 2 init engine
        // 3 build references
        // 4 analyze difference
        // 5 generate chains
        // 6 output



        //1 load
        loadProject(repoPath, mainCommit, branchCommit)


        //2 init search engine
        initSearchEngine()

        //3
        buildClassReferences(developClasses, devSearchEngine)
        buildClassReferences(featureClasses, featSearchEngine)

        //4 analizy
        analyzeDifference()

        //5 generate chains
        generateChains()

        // output data
        output()
    }

    private fun initSearchEngine() {
        logStatus(logFile, "Initialize engines")

        try {
            devSearchEngine = SearcherEngine()
            featSearchEngine = SearcherEngine()

            devSearchEngine.init(developClasses)
            featSearchEngine.init(featureClasses)


            logStatus(logFile, "Initialize engines success")
        } catch (ex: Exception) {
            logStatus(logFile, "Initialize engines error ${ex.message}")
            throw ex
        }
    }

    private fun output(){
        resultPresenter.writeCallChainToFile(callChain)
    }

    private fun generateChains(){
        callChain = dependencyChainBuilder.generateChangedChains(diffResult.changedMethods,featureClasses)
    }

    private fun analyzeDifference() {
        logStatus(logFile, "Analyzing difference")

        try {
            diffResult = differenceAnalyzer.analyzeProjectDifferences(developClasses, featureClasses)

            logStatus(logFile, "Analyzing difference success")

        } catch (ex: Exception) {
            logStatus(logFile, "Analyzing difference error ${ex.message}")
            throw ex
        }
    }

    private fun buildClassReferences(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine) {

        logStatus(logFile, "Project binding")

        try {

            referenceBuilder.bindAll(projectClasses, searchEngine)

            logStatus(logFile, "Project binding success")

        } catch (ex: Exception) {
            logStatus(logFile, "Project binding error ${ex.message}")
            throw ex
        }
    }

    private fun loadProject(repoPath: String, mainCommit: String, branchCommit: String) {

        logStatus(logFile, "Project loading")

        try {
            developClasses = listOf()
            featureClasses = listOf()

            val developFiles = projectLoader.loadProjectFilesFromCommit(repoPath, mainCommit)
                .filterKeys { !it.startsWith("src/test") }

            val featureFiles = projectLoader.loadProjectFilesFromCommit(repoPath, branchCommit)
                .filterKeys { !it.startsWith("src/test") }

            val project = KtFileExtractor.createProject()

            val developKtFiles = developFiles.entries.map { (name, content) ->
                KtFileExtractor.createPsiFile(project, name, content)
            }

            val featureKtFiles = featureFiles.entries.map { (name, content) ->
                KtFileExtractor.createPsiFile(project, name, content)
            }

            developClasses = KtFileMapper.mapKtFilesToClassList(developKtFiles)
            featureClasses = KtFileMapper.mapKtFilesToClassList(featureKtFiles)

            KtFileMapper.linkSuperClasses(developClasses)
            KtFileMapper.linkSuperClasses(featureClasses)

            logStatus(logFile, "Project loading success")
        } catch (ex: Exception) {
            logStatus(logFile, "Project loading error ${ex.message}")
            throw ex
        }
    }

    private fun logStatus(filename: String, content: String) {
        File(filename).appendText(content + System.lineSeparator())
    }
}