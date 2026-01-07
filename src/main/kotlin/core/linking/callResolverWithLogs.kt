package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodReference
import org.example.data.symbol.expression.ExpressionValue
import org.example.data.symbol.expression.MethodValue
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

// Логгер для каждого класса
class ClassResolveLogger(baseDir: String = "logs/resolve_calls") {
    private val writers = mutableMapOf<String, BufferedWriter>()
    private val rootDir = File(baseDir)

    init {
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
        rootDir.mkdirs()
    }

    fun log(cls: KotlinClass, message: String, level: Int = 0) {
        val writer = writers.getOrPut(cls.fullName) {
            val file = File(rootDir, "${cls.name}.txt")
            BufferedWriter(FileWriter(file, false))
        }
        val indent = "  ".repeat(level)
        writer.write("$indent$message\n")
    }

    fun flushAll() {
        writers.values.forEach { it.flush() }
    }

    fun closeAll() {
        writers.values.forEach { it.close() }
        writers.clear()
    }
}

// Разрешение вызовов методов с логированием
class CallResolverWithLogs(private val searchEngine: IProjectSearchEngine) {

    private val logger = ClassResolveLogger()

    fun collect(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            logger.log(cls, "Class: ${cls.fullName}")
            logger.log(cls, "")

            for (method in cls.functionCalls) {
                logger.log(cls, "Method: ${method.fullName}", 1)

                for (expression in method.fullExpressions) {
                    resolveExpression(expression.target, method, cls)
                    resolveExpression(expression.source, method, cls)
                }

                logger.log(cls, "")
            }
        }
        logger.flushAll()
    }

    private fun resolveExpression(
        expr: ExpressionValue?,
        caller: ClassMethod,
        callerClass: KotlinClass
    ) {
        when (expr) {
            is MethodValue -> resolveMethod(expr, caller, callerClass)
        }
    }

    private fun resolveMethod(
        value: MethodValue,
        caller: ClassMethod,
        callerClass: KotlinClass
    ) {
        val callText = "${value.receiverClassName}.${value.methodName}(${value.parameters.joinToString(",") { it.valueType }})"
        logger.log(callerClass, "Call:", 2)
        logger.log(callerClass, "-> $callText", 3)

        val paramTypes = value.parameters.map { it.valueType }
        logger.log(callerClass, "Search Params: ${paramTypes.joinToString(",")}", 3)

        // Поиск класса
        val parentClass = searchEngine.findByClassName(value.receiverClassName)
        if (parentClass == null) {
            logger.log(callerClass, "❌ Class not found: ${value.receiverClassName}", 4)
            return
        }

        // Поиск метода
        val callee = searchEngine.findMethodByClassNameAndMethodNameAndParams(
            className = value.receiverClassName,
            methodName = value.methodName,
            params = paramTypes
        )

        if (callee == null) {
            logger.log(callerClass, "❌ Method not found in ${parentClass.fullName}", 4)
            logger.log(callerClass, "Available methods:", 4)
            parentClass.functionCalls.forEach {
                logger.log(callerClass, "- ${it.fullName}(${it.parameterTypeNames.joinToString(",")})", 5)
            }
            return
        }

        logger.log(callerClass, "✅ Method resolved: ${callee.fullName}", 4)

        // Добавляем вызов в записи
        val reference = MethodReference(
            referenceTargetName = callee.fullName,
            referenceTargetParentClass = parentClass,
            method = value,
            signature = value.methodSignature
        )
        caller.callRecords.add(reference)

        val reverseReference = MethodReference(
            referenceTargetName = caller.fullName,
            referenceTargetParentClass = callerClass,
            method = value,
            signature = ""
        )
        callee.reverseCallRecords.add(reverseReference)
    }

    fun close() {
        logger.closeAll()
    }
}
