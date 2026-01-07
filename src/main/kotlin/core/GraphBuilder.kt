package org.example.core

import org.example.core.interfaces.*
import org.example.core.linking.ClassReferenceBuilder
import org.example.core.linking.DependencyChainBuilder
import org.example.core.psi.KtFileExtractor
import org.example.core.search.SearcherEngine
import org.example.core.utils.KtFileMapper
import org.example.data.analyzer.ProjectDiffResult
import org.example.data.analyzer.ProjectDiffResultOutput
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
    private var callChain = ProjectDiffResultOutput()

    //logs
    private val logFile =           "logs/build_process.txt"
    private val logDifferences =    "logs/project_differences.txt"
    private val logLoadedProject =  "logs/loaded_project" //log for claases

    fun BuildGraph(repoPath: String, mainCommit: String, branchCommit: String) {

        //clear logs
        clearFile()
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
        buildClassReferences(developClasses, devSearchEngine, "develop")
        buildClassReferences(featureClasses, featSearchEngine, "feature")

        //4 analizy
        analyzeDifference()

        //5 generate chains
        generateChains()

        // output data
        output()
    }

    private fun initSearchEngine() {
        logStatus(logFile, "Step 2: initialize engines")

        try {
            devSearchEngine = SearcherEngine()
            featSearchEngine = SearcherEngine()

            devSearchEngine.init(developClasses)
            featSearchEngine.init(featureClasses)


            logStatus(logFile, "Initialize engines success", 1)
        } catch (ex: Exception) {
            logStatus(logFile, "Initialize engines error ${ex.message}", 1)
            throw ex
        }
    }

    private fun output() {
        logStatus(logFile, "Step 6: output")
        try {
            resultPresenter.writeCallChainToFile(callChain)

            logStatus(logFile, "Output success")

        } catch (ex: Exception) {
            logStatus(logFile, "Output error ${ex.message}", 1)
            throw ex
        }
    }

    private fun generateChains() {

        logStatus(logFile, "Step 5: generating chains")
        try {

            val changed = dependencyChainBuilder.generateChangedMethodChains(diffResult.changedMethods, featureClasses)
            val added = dependencyChainBuilder.generateAddedMethodChains(diffResult.changedMethods, featureClasses)
            val removed = dependencyChainBuilder.generateRemovedMethodChains(diffResult.changedMethods, featureClasses)

            callChain.changedMethods = changed
            callChain.addedMethods = added
            callChain.removedMethods = removed

            logStatus(logFile, "Generating chains error success", 1)
        } catch (ex: Exception) {
            logStatus(logFile, "Generating chains error ${ex.message}", 1)
            throw ex
        }
    }

    private fun analyzeDifference() {
        logStatus(logFile, "Step 4: analyzing difference")

        try {

            diffResult = differenceAnalyzer.analyzeProjectDifferences(developClasses, featureClasses)

            LogManager.writeProjectDiffToFile(diffResult, logDifferences)

            logStatus(logFile, "Analyzing difference success", 1)

        } catch (ex: Exception) {
            logStatus(logFile, "Analyzing difference error ${ex.message}", 1)
            throw ex
        }
    }

    private fun buildClassReferences(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine, branchName: String) {

        logStatus(logFile, "Step 3: project binding")

        try {

            referenceBuilder.bindAll(projectClasses, searchEngine, branchName)

            logStatus(logFile, "Project binding success", 1)

        } catch (ex: Exception) {
            logStatus(logFile, "Project binding error ${ex.message}", 1)
            throw ex
        }
    }

    private fun loadProject(repoPath: String, mainCommit: String, branchCommit: String) {

        logStatus(logFile, "Step 1: project loading")

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

            LogManager.logLoadedProject(logLoadedProject+"/develop", developClasses)
            LogManager.logLoadedProject(logLoadedProject+"/feature",featureClasses)

            logStatus(logFile, "Project loading success", 1)
        } catch (ex: Exception) {
            logStatus(logFile, "Project loading error ${ex.message}", 1)
            throw ex
        }
    }

    private fun logStatus(filename: String, content: String, indentLevel: Int = 0) {
        // Создаём отступ: 4 пробела на каждый уровень
        val indent = "    ".repeat(indentLevel)

        // Пишем в файл с отступом
        File(filename).appendText(indent + content + System.lineSeparator())
    }

    private fun clearFile() {
        val file = File(logFile)
        // Перезаписываем пустым содержимым
        file.writeText("")
    }
}