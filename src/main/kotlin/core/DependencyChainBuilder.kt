package org.example.core

import org.example.core.interfaces.IDependencyChainBuilder
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.ClassMethod

class DependencyChainBuilder: IDependencyChainBuilder {
    override fun generateChangedChains(
        methods: List<ClassMethod>,
        allClasses: List<KotlinClass>,
    ): List<MethodCallNode> {
        return methods.map { method ->
            generateChain(method, allClasses)
        }
    }

    override fun generateChain(
        rootMethod: ClassMethod,
        allClasses: List<KotlinClass>
    ): MethodCallNode {
        val visited = mutableSetOf<String>()
        return buildChain(rootMethod, allClasses, visited)
    }

    private fun buildChain(
        method: ClassMethod,
        allClasses: List<KotlinClass>,
        visited: MutableSet<String>
    ): MethodCallNode {
        if (!visited.add(method.fullName)) {
            // Уже обработан, возвращаем пустое звено (или null, если nullable)
            return MethodCallNode(method.fullName, method.function, "", mutableListOf())
        }

        val node = MethodCallNode(
            fullName = method.fullName,
            function = method.function,
            updates = "",
            nextCalls = mutableListOf()
        )

        // Получаем класс метода
        val cls = allClasses.firstOrNull { it.functionCalls.contains(method) } ?: return node

        // Собираем список методов с одинаковым именем: текущий + все родители
        val methodShortName = method.fullName.substringAfter("::")
        val allRelevantMethods = mutableListOf<ClassMethod>()

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
                val callerMethod = reverseCall.parentClass
                    .functionCalls
                    .firstOrNull { it.fullName == reverseCall.fullName }
                    ?: continue

                val childNode = buildChain(callerMethod, allClasses, visited)
                node.nextCalls.add(childNode)
            }
        }

        return node
    }
}