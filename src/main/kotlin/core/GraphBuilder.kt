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
    //state
    private var developClasses: List<KotlinClass> = listOf()
    private var featureClasses: List<KotlinClass> = listOf()

    //separate search engine
    private lateinit var devSearchEngine: IProjectSearchEngine
    private lateinit var featSearchEngine: IProjectSearchEngine
    private lateinit var dependencyChainBuilder: IDependencyChainBuilder

    //output
    private var diffResult = ProjectDiffResult()
    private var callChain = ProjectDiffResultOutput()

    //logs files
    private val buildStepLogFile = "logs/build_process.txt"
    private val projectDifferenceLogFile = "logs/project_differences.txt"

    //log directories
    //имена идут от шагов
    private val loadedProjectFolder = "logs/loaded_project" //log for claases

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
        buildClassReferences()

        //4 analizy
        analyzeDifference()

        //5 generate chains
        generateChains()

        // output data
        output()
    }

    private fun clearFile() {
        LogManager.clearLogFile(buildStepLogFile)
    }

    private fun initSearchEngine() {
        logStatus(buildStepLogFile, "Step 2: initialize engines")

        try {
            devSearchEngine = SearcherEngine()
            devSearchEngine.init(developClasses)

            featSearchEngine = SearcherEngine()
            featSearchEngine.init(featureClasses)

            logStatus(buildStepLogFile, "Initialize engines success", 1)
        } catch (ex: Exception) {
            logStatus(buildStepLogFile, "Initialize engines error ${ex.message}", 1)
            throw ex
        }
    }

    private fun output() {
        logStatus(buildStepLogFile, "Step 6: output")
        try {

            val resultPresenter: IDiffResultPresenter = DiffResultPresenter()

            resultPresenter.writeCallChainToFile(callChain)
            resultPresenter.writeApiCallChainToFile(callChain.apiChain)
            // записываем в файл
            logStatus(buildStepLogFile, "Output success")

        } catch (ex: Exception) {
            logStatus(buildStepLogFile, "Output error ${ex.message}", 1)
            throw ex
        }
    }

    private fun generateChains() {

        logStatus(buildStepLogFile, "Step 5: generating chains")
        try {
            dependencyChainBuilder = DependencyChainBuilder(featSearchEngine)
            val changed = dependencyChainBuilder.generateChangedMethodChains(diffResult.changedMethods, featureClasses)
            callChain.changedMethods = changed

            val apiChain = dependencyChainBuilder.generateApiCallChain(changed)
            callChain.apiChain = apiChain

            logStatus(buildStepLogFile, "Generating chains error success", 1)

        } catch (ex: Exception) {
            logStatus(buildStepLogFile, "Generating chains error ${ex.message}", 1)
            throw ex
        }
    }

    private fun analyzeDifference() {
        logStatus(buildStepLogFile, "Step 4: analyzing difference")

        try {
            val differenceAnalyzer: IProjectDifferenceAnalyzer = DifferenceAnalyzer()

            diffResult = differenceAnalyzer.analyzeProjectDifferences(developClasses, featureClasses)

            LogManager.writeProjectDiffToFile(diffResult, projectDifferenceLogFile)

            logStatus(buildStepLogFile, "Analyzing difference success", 1)

        } catch (ex: Exception) {
            logStatus(buildStepLogFile, "Analyzing difference error ${ex.message}", 1)
            throw ex
        }
    }

    private fun buildClassReferences() {

        logStatus(buildStepLogFile, "Step 3: project binding")

        try {
            val referenceBuilder: IClassReferenceBuilder = ClassReferenceBuilder()
            val logFolder = "logs/class_reference/"

            //develop + logs
            referenceBuilder.bindAll(developClasses, devSearchEngine)

            LogManager.logClassAllMethodExpression(developClasses, "$logFolder/expressions/develop")
            LogManager.logClassAllCalls(developClasses, "$logFolder/calls/develop")


            //feature + logs
            referenceBuilder.bindAll(featureClasses, featSearchEngine)

            LogManager.logClassAllMethodExpression(featureClasses, "$logFolder/expressions/feature")
            LogManager.logClassAllCalls(featureClasses, "$logFolder/calls/feature")

            logStatus(buildStepLogFile, "Project binding success", 1)

        } catch (ex: Exception) {
            logStatus(buildStepLogFile, "Project binding error ${ex.message}", 1)
            throw ex
        }
    }

    private fun loadProject(repoPath: String, mainCommit: String, branchCommit: String) {

        logStatus(buildStepLogFile, "Step 1: project loading")

        try {
            val projectLoader: IProjectLoader = GitLoader()

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

            LogManager.logLoadedProject(loadedProjectFolder + "/develop", developClasses)
            LogManager.logLoadedProject(loadedProjectFolder + "/feature", featureClasses)

            logStatus(buildStepLogFile, "Project loading success", 1)
        } catch (ex: Exception) {
            logStatus(buildStepLogFile, "Project loading error ${ex.message}", 1)
            throw ex
        }
    }

    private fun logStatus(filename: String, content: String, indentLevel: Int = 0) {
        // Создаём отступ: 4 пробела на каждый уровень
        val indent = "    ".repeat(indentLevel)

        // Пишем в файл с отступом
        File(filename).appendText(indent + content + System.lineSeparator())
    }
}