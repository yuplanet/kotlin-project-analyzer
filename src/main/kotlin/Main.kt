package org.example

import org.example.core.CallBuilder
import org.example.plugins.GitLoader
import org.example.core.PsiExtractor
import org.example.mapping.KotlinClassMapper
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
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
        //2 построение связи
        //3 поиск отличий
        //4 вывод

    } catch (e: Exception) {
        e.printStackTrace()
    }
}



fun traceToApiFullStack(
    methodKey: String,
    allMethods: Map<String, KtNamedFunction>,
    callGraph: Map<String, Set<String>>,
    interfaceToImpl: Map<String, List<String>>,
    visited: MutableSet<String> = mutableSetOf()
): List<List<String>> {
    if (methodKey in visited) return emptyList()
    visited.add(methodKey)

    val fn = allMethods[methodKey] ?: return emptyList()
    val methodName = fn.name ?: return listOf(listOf(methodKey))

    // Если метод API, начинаем цепочку с него
    val isApi = isSpringApiMethod(fn)
    val callees = callGraph[methodKey] ?: emptySet()

    if (callees.isEmpty()) {
        return listOf(listOf(methodKey))
    }

    val chains = mutableListOf<List<String>>()
    for (calleeKey in callees) {
        val calleeFn = allMethods[calleeKey] ?: continue

        // Если вызываем интерфейс, подставляем реализации
        val calleeClassName = calleeFn.getStrictParentOfType<KtClassOrObject>()?.name
        val nextKeys = mutableListOf(calleeKey)
        interfaceToImpl[calleeClassName]?.let { implList ->
            val implKeys = implList.mapNotNull { implName ->
                allMethods.entries.find { it.value.getStrictParentOfType<KtClassOrObject>()?.name == implName }?.key
            }
            nextKeys.addAll(implKeys)
        }

        nextKeys.forEach { nextKey ->
            val childChains = traceToApiFullStack(nextKey, allMethods, callGraph, interfaceToImpl, visited)
            if (childChains.isEmpty()) {
                chains.add(listOf(methodKey, nextKey))
            } else {
                childChains.forEach { chain ->
                    chains.add(listOf(methodKey) + chain)
                }
            }
        }
    }

    return if (isApi && chains.isEmpty()) {
        listOf(listOf(methodKey))
    } else {
        chains
    }
}



fun traceToApiInterfaceAware(
    methodKey: String,
    allMethods: Map<String, KtNamedFunction>,
    callGraph: Map<String, Set<String>>,
    interfaceToImpl: Map<String, List<String>>,
    visited: MutableSet<String> = mutableSetOf()
): List<List<String>> {
    if (methodKey in visited) return emptyList()
    visited.add(methodKey)

    val fn = allMethods[methodKey] ?: return emptyList()
    if (isSpringApiMethod(fn)) return listOf(listOf(methodKey))

    val methodName = fn.name ?: return listOf(listOf(methodKey))

    val callers = callGraph.filter { (_, callees) ->
        callees.any { calleeKey ->
            val calleeFn = allMethods[calleeKey]
            calleeFn?.name == methodName ||
                    (calleeFn != null &&
                            interfaceToImpl[calleeFn.getStrictParentOfType<KtClassOrObject>()?.name ?: ""]
                                ?.contains(fn.getStrictParentOfType<KtClassOrObject>()?.name ?: "") == true)
        }
    }.keys

    if (callers.isEmpty()) return listOf(listOf(methodKey))

    val chains = mutableListOf<List<String>>()
    callers.forEach { caller ->
        val parentChains = traceToApiFullStack(caller, allMethods, callGraph, interfaceToImpl, visited)
        parentChains.forEach { chain ->
            chains.add(chain + methodKey)
        }
    }

    return chains
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