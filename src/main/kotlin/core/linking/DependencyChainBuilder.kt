package org.example.core.linking

import org.example.core.interfaces.IDependencyChainBuilder
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass

class DependencyChainBuilder: IDependencyChainBuilder {

    override fun generateChangedChains(
        methods: List<ClassMethod>,
        allClasses: List<KotlinClass>,
    ): List<MethodCallNode> {

        val chain =
            methods.map { method ->
                generateChain(method, allClasses)
            }

        return chain
    }

    override fun generateChain(
        rootMethod: ClassMethod,
        allClasses: List<KotlinClass>
    ): MethodCallNode {

        return buildChain(rootMethod, allClasses)
    }

    private fun buildChain(
        method: ClassMethod,
        allClasses: List<KotlinClass>,
        visited: MutableSet<String> = mutableSetOf()
    ): MethodCallNode {
        // Если уже встречали метод в текущей цепи — прекращаем рекурсию
        if (!visited.add(method.fullName)) {
            return MethodCallNode(method.fullName, method.function, "", mutableListOf())
        }

        val node = MethodCallNode(
            fullName = method.fullName,
            function = method.function,
            updates = "",
            nextCalls = mutableListOf()
        )

        val cls = allClasses.firstOrNull { it.functionCalls.contains(method) } ?: return node
        val methodShortName = method.fullName.substringAfter("::")
        val allRelevantMethods = mutableListOf<ClassMethod>()

        allRelevantMethods += cls.functionCalls.filter { it.fullName.endsWith("::$methodShortName") }
        for (parent in cls.superClasses) {
            allRelevantMethods += parent.functionCalls.filter { it.fullName.endsWith("::$methodShortName") }
        }

        for (m in allRelevantMethods) {
            for (reverseCall in m.reverseCallRecords) {
                val callerMethod = reverseCall.parentClass.functionCalls
                    .firstOrNull { it.fullName == reverseCall.name }

                callerMethod?: continue

                val childNode = buildChain(callerMethod, allClasses, visited.toMutableSet())
                node.nextCalls.add(childNode)
            }
        }

        return node
    }

}