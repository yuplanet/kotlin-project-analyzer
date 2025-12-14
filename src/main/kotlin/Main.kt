package org.example

import org.example.core.CallBuilder
import org.example.core.DiffGraphBuilder
import org.example.core.DifferenceAnalyzer
import org.example.core.PsiExtractor
import org.example.core.interfaces.i_projectLoader
import org.example.data.CallMethod
import org.example.data.KotlinClass
import org.example.data.KotlinMethod
import org.example.mapping.KotlinClassMapper
import org.example.plugins.GitLoader
import java.io.File

fun main() {
    try {
        val repoPath = "C:\\Users\\UlugbekYunusov\\Desktop\\a2s-reloaded"
        val mainCommit = "develop"
        val branchCommit = "feature/xxxx"


        val projectLoader : i_projectLoader = GitLoader()


        val diffGraphBuilder = DiffGraphBuilder(projectLoader)

        // Создаём проект Kotlin с PSI

        val differenceAnalyzer = DifferenceAnalyzer(developClasses, featureClasses)
        val analyzer = differenceAnalyzer.compare()

        saveMethodsToFile(analyzer.added, "added_methods.txt")
        saveMethodsToFile(analyzer.removed, "removed_methods.txt")
        saveMethodsToFile(analyzer.changed, "changed_methods.txt")

        val outputPath = "changed_methods_chains.txt"
        val builder = StringBuilder()
        for (method in analyzer.changed) {
            builder.appendLine("=== Call chain for changed method: ${method.fullName} ===")
            appendCallChain(method, builder, developClasses + featureClasses)
            builder.appendLine()
        }

        File(outputPath).writeText(builder.toString())

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

fun appendCallChain(
    method: KotlinMethod,
    builder: StringBuilder,
    allClasses: List<KotlinClass>,
    indent: String = "",
    visited: MutableSet<String> = mutableSetOf()
) {
    if (method.fullName in visited) return
    visited.add(method.fullName)

    builder.appendLine("$indent${method.fullName}")

    // Берём текущий класс метода
    val cls = allClasses.firstOrNull { it.functionCalls.contains(method) } ?: return

    // Собираем всех "звонящих" методов
    val allCallers = mutableListOf<CallMethod>()
    allCallers.addAll(method.callRecords)

    // Добавляем методы интерфейсов
    for (iface in cls.implementedInterfaces) {
        val ifaceKotlinClass = allClasses.firstOrNull { it.ktClassObject == iface } ?: continue
        val ifaceMethod = ifaceKotlinClass.functionCalls.firstOrNull { it.fullName == method.fullName }
        if (ifaceMethod != null) {
            allCallers.addAll(ifaceMethod.callRecords)
        }
    }

    // Рекурсивно вызываем для всех звонящих
    for (caller in allCallers) {
        val callerMethod = caller.callMethodParentClass.functionCalls
            .firstOrNull { it.fullName == caller.callMethodFullName } ?: continue

        appendCallChain(callerMethod, builder, allClasses, indent + "  ", visited)
    }
}
