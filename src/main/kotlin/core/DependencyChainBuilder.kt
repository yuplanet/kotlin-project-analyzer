package org.example.core

import org.example.core.interfaces.i_dependencyChainBuilder
import org.example.data.CallChainNode
import org.example.data.KotlinClass
import org.example.data.KotlinMethod

class DependencyChainBuilder: i_dependencyChainBuilder {
    override fun generateChangedChains(
        methods: List<KotlinMethod>,
        allClasses: List<KotlinClass>,
    ): List<CallChainNode> {
        return methods.map { method ->
            generateChain(method, allClasses)
        }
    }

    override fun generateChain(
        rootMethod: KotlinMethod,
        allClasses: List<KotlinClass>
    ): CallChainNode {
        val visited = mutableSetOf<String>()
        return buildChain(rootMethod, allClasses, visited)
    }

    private fun buildChain(
        method: KotlinMethod,
        allClasses: List<KotlinClass>,
        visited: MutableSet<String>
    ): CallChainNode {
        if (!visited.add(method.fullName)) {
            // Уже обработан, возвращаем пустое звено (или null, если nullable)
            return CallChainNode(method.fullName, method.function, "", mutableListOf())
        }

        val node = CallChainNode(
            fullName = method.fullName,
            function = method.function,
            updates = "",
            nextCalls = mutableListOf()
        )

        // Получаем класс метода
        val cls = allClasses.firstOrNull { it.functionCalls.contains(method) } ?: return node

        // Собираем список методов с одинаковым именем: текущий + все родители
        val methodShortName = method.fullName.substringAfter("::")
        val allRelevantMethods = mutableListOf<KotlinMethod>()

        // Текущий класс
        cls.functionCalls.firstOrNull { it.fullName.endsWith("::$methodShortName") }?.let { allRelevantMethods.add(it) }


        if(method.fullName.contains("RcsService"))
            print(1)

        // Родители
        for (parent in cls.superClasses) {
            parent.functionCalls.firstOrNull { it.fullName.endsWith("::$methodShortName") }?.let { allRelevantMethods.add(it) }
        }

        // Пробегаем по всем найденным методам и их reverseCallRecords
        for (m in allRelevantMethods) {
            for (reverseCall in m.reverseCallRecords) {
                val callerMethod = reverseCall.callerMethodParentClass
                    .functionCalls
                    .firstOrNull { it.fullName == reverseCall.callerMethodFullName }
                    ?: continue

                val childNode = buildChain(callerMethod, allClasses, visited)
                node.nextCalls.add(childNode)
            }
        }

        return node
    }
}