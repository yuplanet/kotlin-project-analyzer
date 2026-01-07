package org.example.core

import org.example.data.symbol.KotlinClass
import java.io.File

object LogManager {

    fun logLoadedProject(path: String, project: List<KotlinClass>) {
        val logDir = File(path)
        if (logDir.exists()) {
            logDir.deleteRecursively() // удалить старые логи
        }
        logDir.mkdirs()

        project.forEach { kotlinClass ->
            logClass(kotlinClass, logDir)
        }
    }

    private fun logClass(kotlinClass: KotlinClass, logDir: File) {
        val file = File(logDir, "${kotlinClass.name}.txt")
        file.bufferedWriter().use { writer ->
            writer.appendLine("KotlinClass: ${kotlinClass.fullName}")
            writer.appendLine("Path: ${kotlinClass.path}")
            writer.appendLine("Type: ${kotlinClass.ktClassObjectType}")
            writer.appendLine("Annotations: ${kotlinClass.annotations.joinToString(", ")}")
            writer.appendLine("")

            writer.appendLine("Properties:")
            kotlinClass.propertyReferences.forEach { prop ->
                writer.appendLine("  - ${prop.name}: ${prop.type}")
                if (prop.expression.isNotEmpty()) {
                    writer.appendLine("    Expressions:")
                    prop.expression.forEach { expr ->
                        writer.appendLine("      ${expr.target?.rawValue} = ${expr.source?.rawValue}")
                    }
                }
            }
            writer.appendLine("")

            writer.appendLine("Parameters:")
            kotlinClass.parameterReferences.forEach { param ->
                writer.appendLine("  - ${param.name}: ${param.type}")
                if (param.expression.isNotEmpty()) {
                    writer.appendLine("    Expressions:")
                    param.expression.forEach { expr ->
                        writer.appendLine("      ${expr.target?.rawValue} = ${expr.source?.rawValue}")
                    }
                }
            }
            writer.appendLine("")

            writer.appendLine("Functions:")
            kotlinClass.functionCalls.forEach { func ->
                writer.appendLine("  - ${func.fullName}: ${func.returnType}")
                writer.appendLine("    Parameters: ${func.parameterTypeNames.joinToString(", ")}")
                if (func.properties.isNotEmpty()) {
                    writer.appendLine("    Properties:")
                    func.properties.forEach { prop ->
                        writer.appendLine("      ${prop.name}: ${prop.type}")
                    }
                }
                if (func.fullExpressions.isNotEmpty()) {
                    writer.appendLine("    Expressions:")
                    func.fullExpressions.forEach { expr ->
                        writer.appendLine("      ${expr.target?.rawValue} = ${expr.source?.rawValue}")
                    }
                }
            }
            writer.appendLine("")

            if (kotlinClass.subClasses.isNotEmpty()) {
                writer.appendLine("SubClasses:")
                kotlinClass.subClasses.forEach { sub ->
                    writer.appendLine("  - ${sub.fullName}")
                }
            }

            if (kotlinClass.superClasses.isNotEmpty()) {
                writer.appendLine("SuperClasses:")
                kotlinClass.superClasses.forEach { sup ->
                    writer.appendLine("  - ${sup.fullName}")
                }
            }
        }
    }
}