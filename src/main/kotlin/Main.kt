package org.example

import org.example.git.GitLoader
import org.example.parser.PsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import java.io.File
import kotlin.text.contains
import kotlin.text.get

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
        val project = PsiUtils.createProject()

        val developKtFiles = developFiles.entries.map { (name, content) ->
            PsiUtils.createPsiFile(project, name, content)
        }

        val featureKtFiles = featureFiles.entries.map { (name, content) ->
            PsiUtils.createPsiFile(project, name, content)
        }
        val interfaceToImpl = buildInterfaceToImplMap(featureKtFiles)

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

        val reverseCallGraph = mutableMapOf<String, MutableSet<String>>()
        callGraph.forEach { (caller, callees) ->
            callees.forEach { callee ->
                reverseCallGraph.computeIfAbsent(callee) { mutableSetOf() }.add(caller)
            }
        }

        val modifiedMethods = featureMap.filter { (key, fn) ->
            val oldFn = developMap[key]
            oldFn == null || collectCalls(oldFn) != collectCalls(fn) || collectApi(oldFn) != collectApi(fn)
        }.keys


        val outputFile = File("api_changes.txt")

// Создаём файл или очищаем, если уже существует
        outputFile.writeText("API Changes Report\n\n")

        // 9️⃣ Выводим цепочки для изменённых методов
        modifiedMethods.forEach { methodKey ->
            val chains = traceToApiInterfaceAware(methodKey, allMethods, callGraph, interfaceToImpl, mutableSetOf())
            if (chains.isEmpty()) {
                outputFile.appendText("No API uses $methodKey\n\n")
            } else {
                chains.forEach { chain ->
                    outputFile.appendText(chain.joinToString(" -> ") { step ->
                        step.substringAfterLast('/') // или после последнего ::, чтобы убрать путь
                    })
                    outputFile.appendText("\n")
                }
                outputFile.appendText("\n")
            }
        }


        println("API changes report saved to ${outputFile.absolutePath}")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun buildInterfaceToImplMap(ktFiles: List<KtFile>): Map<String, List<String>> {
    val beans = ktFiles
        .flatMap { it.collectDescendantsOfType<KtClassOrObject> { true } }
        .filter { clazz ->
            clazz.annotationEntries.any {
                val ann = it.shortName?.asString()
                ann in listOf("Service", "Repository", "Component")
            }
        }

    val map = mutableMapOf<String, MutableList<String>>()
    beans.forEach { impl ->
        impl.superTypeListEntries.mapNotNull { superType ->
            superType.typeAsUserType?.referencedName?.let { iface ->
                map.computeIfAbsent(iface) { mutableListOf() }.add(impl.name!!)
            }
        }
    }
    return map
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
        val parentChains = traceToApiInterfaceAware(caller, allMethods, callGraph, interfaceToImpl, visited.toMutableSet())
        parentChains.forEach { chain ->
            chains.add(chain + methodKey)
        }
    }
    return chains
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