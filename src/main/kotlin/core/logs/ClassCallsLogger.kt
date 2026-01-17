package org.example.core.logs

import org.example.data.symbol.KotlinClass
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

object ClassCallsLogger {
    private fun resetLogDirectory(path: String) {
        val logDir = File(path)
        if (logDir.exists()) {
            logDir.deleteRecursively()
        }
        logDir.mkdirs()
    }

    fun logAllCalls(logPath: String, projectClasses: List<KotlinClass>) {
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
}