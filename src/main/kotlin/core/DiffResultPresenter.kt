package org.example.core

import org.example.core.interfaces.i_diffResultPresenter
import org.example.data.CallMethod
import org.example.data.DiffResult
import org.example.data.KotlinClass
import org.example.data.KotlinMethod
import java.io.File

class DiffResultPresenter : i_diffResultPresenter{

    override fun writeCallChainToFile(result: DiffResult){
        outResults(result)
    }

    private fun outResults(result: DiffResult) {
        saveMethodsToFile(result.added, "added_methods.txt")
        saveMethodsToFile(result.removed, "removed_methods.txt")
        saveMethodsToFile(result.changed, "changed_methods.txt")

        val outputPath = "changed_methods_chains.txt"
        val builder = StringBuilder()
        for (method in result.changed) {
            builder.appendLine("=== Call chain for changed method: ${method.fullName} ===")
            appendCallChain(method, builder, developClasses + featureClasses)
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
}