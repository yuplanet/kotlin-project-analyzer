package org.example

import org.example.core.CallBuilder
import org.example.core.DifferenceAnalyzer
import org.example.plugins.GitLoader
import org.example.core.PsiExtractor
import org.example.data.KotlinMethod
import org.example.mapping.KotlinClassMapper
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import java.io.File
import kotlin.text.contains

fun main() {
    try {
        val repoPath = "C:\\Users\\UlugbekYunusov\\Desktop\\a2s-reloaded"
        val mainCommit = "develop"
        val branchCommit = "feature/xxxx"

        // Загружаем файлы из Git
        val developFiles = GitLoader.loadFilesFromCommit(repoPath, mainCommit)
            .filterKeys { !it.startsWith("src/test") }

        val featureFiles = GitLoader.loadFilesFromCommit(repoPath, branchCommit)
            .filterKeys { !it.startsWith("src/test") }

        // Создаём проект Kotlin с PSI
        val project = PsiExtractor.createProject()

        val developKtFiles = developFiles.entries.map { (name, content) ->
            PsiExtractor.createPsiFile(project, name, content)
        }

        val featureKtFiles = featureFiles.entries.map { (name, content) ->
            PsiExtractor.createPsiFile(project, name, content)
        }

        val developClasses = KotlinClassMapper.mapKtFilesToClasses(developKtFiles)
        val featureClasses = KotlinClassMapper.mapKtFilesToClasses(featureKtFiles)

        CallBuilder.buildCallRecordsSimple(developClasses)
        CallBuilder.buildCallRecordsSimple(featureClasses)
        val differenceAnalyzer = DifferenceAnalyzer(developClasses, featureClasses)
        val analyzer = differenceAnalyzer.compare()

        saveMethodsToFile(analyzer.added, "added_methods.txt")
        saveMethodsToFile(analyzer.removed, "removed_methods.txt")
        saveMethodsToFile(analyzer.changed, "changed_methods.txt")

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun saveMethodsToFile(methods: List<KotlinMethod>, outputPath: String) {
    val builder = StringBuilder()

    for (method in methods) {
        builder.appendLine(method.fullName)
    }

    File(outputPath).writeText(builder.toString())
}