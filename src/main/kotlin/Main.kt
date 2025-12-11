package org.example

import org.example.git.GitLoader
import org.example.parser.PsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
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
        val project = PsiUtils.createProject()

        val developKtFiles = developFiles.entries.map { (name, content) ->
            PsiUtils.createPsiFile(project, name, content)
        }

        val featureKtFiles = featureFiles.entries.map { (name, content) ->
            PsiUtils.createPsiFile(project, name, content)
        }

        // Извлекаем методы
        val developMethods = extractMethods(developKtFiles)
        val featureMethods = extractMethods(featureKtFiles)

        // Используем уникальные ключи для методов: fileName::methodName
        fun methodKey(file: KtFile, fn: KtNamedFunction) = "${file.name}::${fn.name ?: "<anonymous>"}"

        val developMap = developMethods.associateBy { methodKey(it.containingKtFile, it) }
        val featureMap = featureMethods.associateBy { methodKey(it.containingKtFile, it) }

        val allMethods = (developMethods + featureMethods).associateBy { methodKey(it.containingKtFile, it) }

        val callGraph = mutableMapOf<String, MutableSet<String>>()
        allMethods.forEach { (key, fn) ->
            collectCalls(fn).forEach { callText ->
                allMethods.values.find { it.name != null && callText.contains(it.name!!) }?.let { callee ->
                    val calleeKey = methodKey(callee.containingKtFile, callee)
                    callGraph.computeIfAbsent(key) { mutableSetOf() }.add(calleeKey)
                }
            }
        }

        val modifiedMethods = featureMap.filter { (key, fn) ->
            val oldFn = developMap[key]
            oldFn == null || collectCalls(oldFn) != collectCalls(fn) || collectApi(oldFn) != collectApi(fn)
        }.keys

        fun traceToApi(methodKey: String, visited: MutableSet<String> = mutableSetOf()): List<List<String>> {
            if (methodKey in visited) return emptyList()
            visited.add(methodKey)

            val fn = allMethods[methodKey] ?: return emptyList()

            // Если это API-метод, цепочка заканчивается
            if (isSpringApiMethod(fn)) return listOf(listOf(methodKey))

            // Находим все методы, которые вызывают этот метод
            val callers = callGraph.filter { it.value.contains(methodKey) }.keys
            if (callers.isEmpty()) return listOf(listOf(methodKey))

            val chains = mutableListOf<List<String>>()
            callers.forEach { caller ->
                val parentChains = traceToApi(caller, visited)
                parentChains.forEach { chain ->
                    chains.add(chain + methodKey)
                }
            }
            return chains
        }

        // 9️⃣ Выводим цепочки для изменённых методов
        modifiedMethods.forEach { methodKey ->
            val chains = traceToApi(methodKey)
            if (chains.isEmpty()) println("No API uses $methodKey")
            else chains.forEach { chain ->
                println(chain.joinToString(" -> "))
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
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
