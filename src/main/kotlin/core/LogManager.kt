package org.example.core

import org.example.data.analyzer.ProjectDiffResult
import java.io.File

object LogManager {

    fun writeProjectDiffToFile(result: ProjectDiffResult, path: String) {
        val file = File(path)

        // удалить старый лог, если существует
        if (file.exists())
            file.delete()

        // создать родительские директории при необходимости
        file.parentFile?.mkdirs()

        val logContent = toLogString(result)
        file.writeText(logContent)
    }

    fun toLogString(result: ProjectDiffResult): String {
        return buildString {
            appendLine("=== ProjectDiffResult ===")

            appendLine("Added methods: ${result.addedMethods.size}")
            result.addedMethods.forEach { appendLine("  + ${it.fullName}") }

            appendLine("Removed methods: ${result.removedMethods.size}")
            result.removedMethods.forEach { appendLine("  - ${it.fullName}") }

            appendLine("Changed methods: ${result.changedMethods.size}")
            result.changedMethods.forEach { appendLine("  * ${it.fullName}") }

            appendLine()

            appendLine("Added parameters: ${result.addedParameters.size}")
            result.addedParameters.forEach { appendLine("  + ${it.name}: ${it.type}") }

            appendLine("Removed parameters: ${result.removedParameters.size}")
            result.removedParameters.forEach { appendLine("  - ${it.name}: ${it.type}") }

            appendLine("Changed parameters: ${result.changedParameters.size}")
            result.changedParameters.forEach { appendLine("  * ${it.name}: ${it.type}") }

            appendLine()

            appendLine("Added properties: ${result.addedProperties.size}")
            result.addedProperties.forEach { appendLine("  + ${it.name}: ${it.type}") }

            appendLine("Removed properties: ${result.removedProperties.size}")
            result.removedProperties.forEach { appendLine("  - ${it.name}: ${it.type}") }

            appendLine("Changed properties: ${result.changedProperties.size}")
            result.changedProperties.forEach { appendLine("  * ${it.name}: ${it.type}") }
        }
    }
}