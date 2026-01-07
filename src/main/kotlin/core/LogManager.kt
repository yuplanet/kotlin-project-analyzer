package org.example.core

import org.example.data.symbol.KotlinClass
import org.example.data.symbol.expression.AssignmentExpression
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

object LogManager {


    //log after expression bind
    fun logClassAllMethodExpression(classes: List<KotlinClass>, logFolder: String = "") {
        val folder = File(logFolder)
        if (folder.exists() && folder.isFile) folder.delete()
        folder.mkdirs()

        classes.forEach { kotlinClass ->
            logClassAllMethodExpression(kotlinClass, folder)
        }
    }

    private fun logClassAllMethodExpression(kotlinClass: KotlinClass, folder: File) {
        val file = File(folder, "${kotlinClass.name}.txt")
        BufferedWriter(FileWriter(file, false)).use { writer ->

            fun log(message: String, level: Int = 0) {
                val indent = "  ".repeat(level)
                writer.write("$indent$message\n")
            }

            // ---- Основная информация о классе ----
            log("===== KotlinClass: ${kotlinClass.fullName} =====", 0)
            log("Path: ${kotlinClass.path}", 1)
            log("Type: ${kotlinClass.ktClassObjectType}", 1)
            log("Annotations: ${kotlinClass.annotations.joinToString(", ")}", 1)
            log("")

            // ---- Свойства ----
            log("Properties:", 0)
            kotlinClass.propertyReferences.forEach { prop ->
                log("- ${prop.name}: ${prop.type}", 1)
                if (prop.expression.isNotEmpty()) {
                    log("Expressions:", 2)
                    prop.expression.forEach { expr ->
                        logAssignmentExpression(expr, writer, 3)
                    }
                }
            }
            log("")

            // ---- Методы ----
            log("Methods:", 0)
            kotlinClass.functionCalls.forEach { method ->
                log("- ${method.fullName}: ${method.returnType}", 1)
                if (method.parameterTypeNames.isNotEmpty()) {
                    log("Parameters: ${method.parameterTypeNames.joinToString(", ")}", 2)
                }
                if (method.fullExpressions.isNotEmpty()) {
                    log("Expressions:", 2)
                    method.fullExpressions.forEach { expr ->
                        logAssignmentExpression(expr, writer, 3)
                    }
                }
            }

            log("\n===== End of ${kotlinClass.fullName} =====", 0)
        }
    }

    private fun logAssignmentExpression(expr: AssignmentExpression, writer: BufferedWriter, level: Int) {
        val indent = "  ".repeat(level)

        writer.write("\n$indent Raw: ${expr.getRawExpression}\n")
        writer.write("$indent Signature: ${expr.getExpressionSignature}\n")
    }



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



    //////log after load
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