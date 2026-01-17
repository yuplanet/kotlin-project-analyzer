package org.example.core

import org.example.core.interfaces.*
import org.example.core.linking.ClassReferenceBuilder
import org.example.core.linking.DependencyChainBuilder
import org.example.core.logs.ClassCallsLogger
import org.example.core.logs.ClassExpressionsLogger
import org.example.core.logs.ClassLogger
import org.example.core.logs.logsPath
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
    private val devSearchEngine: IProjectSearchEngine = SearcherEngine()
    private val featSearchEngine: IProjectSearchEngine = SearcherEngine()
    private lateinit var dependencyChainBuilder: IDependencyChainBuilder

    //output
    private var diffResult = ProjectDiffResult()
    private var callChain = ProjectDiffResultOutput()

    //logs files
    private val logFile = logsPath.processSteplogFile
    private val projectDifferenceLogFile = "logs/project_differences.txt"


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
        //2 init search engine
        loadProject(repoPath, mainCommit, branchCommit)

        //3
        buildClassReferences()

        //4 analizy // to do correct
        analyzeDifference()

        //5 generate chains
        generateChains()

        // output data
        output()
    }

    private fun clearFile() {

        val dir = File(logsPath.logFoler)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }

        logStatus("***log folder generated")
    }

    private fun output() {
        logStatus( "Step 6: output")
        try {

            val resultPresenter: IDiffResultPresenter = DiffResultPresenter()

            resultPresenter.writeCallChainToFile(callChain)
            resultPresenter.writeApiCallChainToFile(callChain.apiChain, devSearchEngine.getAllMethods())
            // записываем в файл
            logStatus( "Output success")

        } catch (ex: Exception) {
            logStatus( "Output error ${ex.message}", 1)
            throw ex
        }
    }

    private fun generateChains() {

        logStatus( "Step 5: generating chains")
        try {
            dependencyChainBuilder = DependencyChainBuilder(featSearchEngine)
            val changed = dependencyChainBuilder.generateChangedMethodChains(diffResult.changedMethods, featureClasses)
            callChain.changedMethods = changed

            val apiChain = dependencyChainBuilder.generateApiCallChain(changed)
            callChain.apiChain = apiChain

            logStatus( "Generating chains error success", 1)

        } catch (ex: Exception) {
            logStatus( "Generating chains error ${ex.message}", 1)
            throw ex
        }
    }

    private fun analyzeDifference() {
        logStatus( "Step 4: analyzing difference")

        try {
            val differenceAnalyzer: IProjectDifferenceAnalyzer = DifferenceAnalyzer()

            diffResult = differenceAnalyzer.analyzeProjectDifferences(developClasses, featureClasses)

            LogManager.writeProjectDiffToFile(diffResult, projectDifferenceLogFile)

            logStatus( "Analyzing difference success", 1)

        } catch (ex: Exception) {
            logStatus( "Analyzing difference error ${ex.message}", 1)
            throw ex
        }
    }

    private fun buildClassReferences() {

        logStatus("Step 3: project binding")

        try {
            val referenceBuilder: IClassReferenceBuilder = ClassReferenceBuilder()

            //develop + logs
            referenceBuilder.init(devSearchEngine)
            referenceBuilder.collectExpressions()
            referenceBuilder.collectCallsFromExpressions()

            ClassCallsLogger.logAllCalls(logsPath.classesCallsFolder + "/develop", developClasses)
            ClassExpressionsLogger.logAllExpressions(logsPath.classesExpressionsFolder + "/develop", developClasses)

            //feature + logs
            referenceBuilder.init(featSearchEngine)
            referenceBuilder.collectExpressions()
            referenceBuilder.collectCallsFromExpressions()

            ClassCallsLogger.logAllCalls(logsPath.classesCallsFolder + "/feature", featureClasses)
            ClassExpressionsLogger.logAllExpressions(logsPath.classesExpressionsFolder + "/feature", featureClasses)

            logStatus("Project bind success", 1)

        } catch (ex: Exception) {
            logStatus("Project bind error ${ex.message}", 1)
            throw ex
        }
    }

    private fun loadProject(repoPath: String, mainCommit: String, branchCommit: String) {

        logStatus("Step 1: project loading")

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

            logStatus("Project loaded success", 1)

            logStatus("Step 2: initialize engines")

            devSearchEngine.init(developClasses)
            featSearchEngine.init(featureClasses)

            logStatus("Initialize engines success", 1)

            ClassLogger.logProjectClasses(logsPath.loadedProjectFolder + "/develop", developClasses)
            ClassLogger.logProjectClasses(logsPath.loadedProjectFolder + "/feature", featureClasses)
        } catch (ex: Exception) {
            logStatus("Project loading error ${ex.message}", 1)
            throw ex
        }
    }

    private fun logStatus(logContent: String, indentLevel: Int = 0) {
        // Создаём отступ: 4 пробела на каждый уровень
        val indent = "    ".repeat(indentLevel)

        // Пишем в файл с отступом
        File(logFile).appendText(indent + logContent + System.lineSeparator())
    }
}