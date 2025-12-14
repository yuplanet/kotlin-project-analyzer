package org.example.core

import org.example.core.interfaces.i_diffResultPresenter
import org.example.data.DiffResult
import org.example.data.KotlinClass
import org.example.data.KotlinMethod
import java.io.File

class DiffResultPresenter : i_diffResultPresenter {

    override fun writeCallChainToFile(result: DiffResult, allClasses: List<KotlinClass>) {

        outResults(result, allClasses)
    }

    private fun outResults(result: DiffResult, allClasses: List<KotlinClass>) {
        saveMethodsToFile(result.added, "added_methods.txt")
        saveMethodsToFile(result.removed, "removed_methods.txt")
        saveMethodsToFile(result.changed, "changed_methods.txt")

        val outputPath = "changed_methods_chains.txt"
        val builder = StringBuilder()
        for (method in result.changed) {
            builder.appendLine("=== Call chain for changed method: ${method.fullName} ===")
            appendCallChain(method, builder, allClasses)
            builder.appendLine()
        }

        File(outputPath).writeText(builder.toString())
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
        allClasses: List<KotlinClass>,  // добавляем сюда
        indent: String = "",
        visited: MutableSet<String> = mutableSetOf()
    ) {
        if (!visited.add(method.fullName)) return

        builder.appendLine("$indent${method.fullName}")

        // идём ТОЛЬКО по reverse-графу
        for (reverseCall in method.reverseCallRecords) {

            val callerMethod = reverseCall.callerMethodParentClass
                .functionCalls
                .firstOrNull { it.fullName == reverseCall.callerMethodFullName }
                ?: continue

            appendCallChain(
                callerMethod,
                builder,
                allClasses,
                indent + "  ",
                visited
            )
        }

        // 2. Поиск метода в родительских классах
        val cls = allClasses.firstOrNull { it.functionCalls.contains(method) } ?: return
        for (parent in cls.superClasses) {
            val parentMethod = parent.functionCalls
                .firstOrNull { it.fullName.substringAfter("::") == method.fullName.substringAfter("::") }
                ?: continue

            appendCallChain(parentMethod, builder, allClasses, indent + "  ", visited)
        }
    }
}