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

        // Загружаем файлы из Git
        val developFiles = GitLoader.loadFilesFromCommit(repoPath, mainCommit)
        val featureFiles = GitLoader.loadFilesFromCommit(repoPath, branchCommit)

        // Создаём проект Kotlin с PSI
        val project = createProject()

        val developKtFiles = developFiles.entries.map { (name, content) ->
            createPsiFile(project, name, content)
        }

        val featureKtFiles = featureFiles.entries.map { (name, content) ->
            createPsiFile(project, name, content)
        }

        // Извлекаем методы
        val developMethods = extractMethods(developKtFiles)
        val featureMethods = extractMethods(featureKtFiles)

        // Используем уникальные ключи для методов: fileName::methodName
        fun methodKey(file: KtFile, fn: KtNamedFunction) = "${file.name}::${fn.name ?: "<anonymous>"}"

        val developMap = developMethods.associateBy { methodKey(it.containingKtFile, it) }
        val featureMap = featureMethods.associateBy { methodKey(it.containingKtFile, it) }


        // Сравнение методов
        featureMap.forEach { (key, featureFn) ->
            val developFn = developMap[key]

            val newCalls = collectCalls(featureFn)
            val newApi = collectApi(featureFn)

            if (developFn == null) {
                // Метод новый
                println("=== Added method: $key ===")
                println("Full calls: $newCalls")
                println("API used: $newApi")
            } else {
                val oldCalls = collectCalls(developFn)
                val oldApi = collectApi(developFn)

                if (oldCalls != newCalls) {
                    println("=== Modified method: $key ===")
                    println("Old calls: $oldCalls")
                    println("New calls: $newCalls")

                    val addedApi = newApi - oldApi
                    val removedApi = oldApi - newApi
                    if (addedApi.isNotEmpty()) println("API added: $addedApi")
                    if (removedApi.isNotEmpty()) println("API removed: $removedApi")
                }
            }
        }

        // Удалённые методы
        developMap.forEach { (key, developFn) ->
            if (key !in featureMap) {
                val oldCalls = collectCalls(developFn)
                val oldApi = collectApi(developFn)
                println("=== Deleted method: $key ===")
                println("Full calls: $oldCalls")
                println("API used: $oldApi")
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

fun isSpringApiMethod(fn: KtNamedFunction): Boolean {
    return fn.annotationEntries.any {
        val text = it.shortName?.asString() ?: ""
        text in listOf("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping")
    }
}

fun buildCallChain(fn: KtNamedFunction, methodMap: Map<String, KtNamedFunction>, visited: MutableSet<String> = mutableSetOf()): List<String> {
    val key = "${fn.containingKtFile.name}::${fn.name ?: "<anonymous>"}"
    if (key in visited) return emptyList()
    visited.add(key)

    val calls = collectCalls(fn)
    val chain = mutableListOf<String>()
    for (call in calls) {
        // Находим метод в нашем проекте
        val targetFn = methodMap.values.find { it.name == call || it.name?.let { n -> call.contains(n) } == true }
        if (targetFn != null) {
            chain.add("${key} -> ${targetFn.name}")
            chain.addAll(buildCallChain(targetFn, methodMap, visited))
        }
    }
    return chain
}


data class FunctionInfo(
    val name: String,
    val file: KtFile,
    val node: KtNamedFunction,
    val calls: List<String>
)
