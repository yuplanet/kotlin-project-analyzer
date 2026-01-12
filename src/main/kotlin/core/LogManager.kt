package org.example.core

import org.example.data.analyzer.ProjectDiffResult
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.expression.AssignmentExpression
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

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




    /////////////////////////////////log diferences







///////////////////////////////////////////////////////////////

    private fun resetLogDirectory(path: String) {
        val logDir = File(path)
        if (logDir.exists()) {
            logDir.deleteRecursively()
        }
        logDir.mkdirs()
    }

    fun logClassAllCalls(projectClasses: List<KotlinClass>, logPath: String) {
        resetLogDirectory(logPath)

        val logDir = File(logPath)

        for (cls in projectClasses) {
            val file = File(logDir, "${cls.name}.txt")

            BufferedWriter(FileWriter(file, false)).use { writer ->

                fun log(message: String = "", level: Int = 0) {
                    val indent = "  ".repeat(level)
                    writer.write("$indent$message\n")
                }

                // ===== CLASS =====
                log("Class: ${cls.fullName}")
                log()

                // ===== METHODS =====
                log("Methods:")
                for (method in cls.functionCalls) {
                    log("Method: ${method.fullName}", 1)

                    if (method.callRecords.isNotEmpty()) {
                        log("calls:", 2)
                        for (call in method.callRecords) {
                            log("-> ${call.referenceTargetName}", 3)
                        }
                    }

                    if (method.reverseCallRecords.isNotEmpty()) {
                        log("called by:", 2)
                        for (call in method.reverseCallRecords) {
                            log("<- ${call.referenceTargetName}", 3)
                        }
                    }

                    log()
                }

                // ===== FIELDS =====
                if (cls.propertyReferences.isNotEmpty()) {
                    log("Fields:")
                    for (field in cls.propertyReferences) {
                        log("Field: ${field.name}:${field.type}", 1)

                        if (field.callRecords.isNotEmpty()) {
                            log("used in:", 2)
                            for (call in field.callRecords) {
                                log("<- ${call.referenceTargetName}", 3)
                            }
                        }

                        log()
                    }
                }

                // ===== PARAMETERS =====
                if (cls.parameterReferences.isNotEmpty()) {
                    log("Parameters:")
                    for (param in cls.parameterReferences) {
                        log("Param: ${param.name}:${param.type}", 1)

                        if (param.callRecords.isNotEmpty()) {
                            log("used in:", 2)
                            for (call in param.callRecords) {
                                log("<- ${call.referenceTargetName}", 3)
                            }
                        }

                        log()
                    }
                }

                writer.flush()
            }
        }
    }

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

            log("\n\n===== End of ${kotlinClass.fullName} =====", 0)
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