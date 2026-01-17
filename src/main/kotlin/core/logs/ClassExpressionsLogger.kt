package org.example.core.logs

import org.example.data.symbol.KotlinClass
import org.example.data.symbol.expression.AssignmentExpression
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

object ClassExpressionsLogger {
    //log after expression bind
    fun logAllExpressions( logFolder: String, classes: List<KotlinClass>) {
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
                if (prop.fullExpressions.isNotEmpty()) {
                    log("Expressions:", 2)
                    prop.fullExpressions.forEach { expr ->
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
}