package org.example

import org.example.git.GitLoader
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

fun main() {
    try {
        val repoPath = "C:\\Users\\UlugbekYunusov\\Desktop\\a2s-reloaded"
        val mainCommit = "develop"
        val branchCommit = "feature/xxxx"

        val developFiles = GitLoader.loadFilesFromCommit(repoPath, mainCommit)
        val featureFiles = GitLoader.loadFilesFromCommit(repoPath, branchCommit)

        val project = createProject()

        val developKtFiles = developFiles.entries.map { (name, content) ->
            createPsiFile(project, name, content)
        }

        val featureKtFiles = featureFiles.entries.map { (name, content) ->
            createPsiFile(project, name, content)
        }

        val developMethods = extractMethods(developKtFiles)
        val featureMethods = extractMethods(featureKtFiles)

        val developCalls = developMethods.associateBy { it.name ?: "<anonymous>" }
        val featureCalls = featureMethods.associateBy { it.name ?: "<anonymous>" }

        featureCalls.forEach { (name, featureFn) ->
            val developFn = developCalls[name]

            if (developFn == null) {
                // Метод новый
                println("Added method: $name")
            } else {
                // Метод существует в обеих ветках, сравниваем вызовы
                val oldCalls = collectCalls(developFn)
                val newCalls = collectCalls(featureFn)
                if (oldCalls != newCalls) {
                    println("Modified method '$name': $oldCalls -> $newCalls")
                }
            }
        }

        developCalls.forEach { (name, developFn) ->
            if (name !in featureCalls) {
                println("Deleted method: $name")
            }
        }


    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun createProject(): Project {
    val disposable: Disposable = Disposer.newDisposable()

    val configuration = CompilerConfiguration().apply {
        put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_21)
    }

    val environment = KotlinCoreEnvironment.createForProduction(
        disposable,
        configuration,
        EnvironmentConfigFiles.JVM_CONFIG_FILES
    )

    return environment.project
}

fun createPsiFile(project: Project, fileName: String, code: String): KtFile {
    // Используем KtPsiFactory, чтобы создать KtFile в памяти
    val psiFactory = KtPsiFactory(project, false)
    return psiFactory.createFile(fileName, code)
}


fun extractMethods(ktFiles: List<KtFile>): List<KtNamedFunction> {
    return ktFiles.flatMap { ktFile ->
        ktFile.collectDescendantsOfType<KtNamedFunction> { true }
    }
}

fun collectCalls(fn: KtNamedFunction): List<String> =
    fn.bodyExpression
        ?.collectDescendantsOfType<KtCallExpression> { true }
        ?.map { it.text } ?: emptyList()

fun collectApi(fn: KtNamedFunction): List<String> =
    fn.bodyExpression
        ?.collectDescendantsOfType<KtCallExpression> { true }
        ?.mapNotNull { it.calleeExpression?.text } ?: emptyList()



data class FunctionInfo(
    val name: String,
    val file: KtFile,
    val node: KtNamedFunction,
    val calls: List<String>
)
