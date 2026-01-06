package org.example.core.linking

import org.example.core.interfaces.IDependencyChainBuilder
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass

class DependencyChainBuilder: IDependencyChainBuilder {

    override fun generateChangedMethodChains(
        methods: List<ClassMethod>,
        allClasses: List<KotlinClass>
    ): List<MethodCallNode> {
        return methods.map { method ->
            buildChain(method, mutableSetOf())
        }
    }


    override fun generateAddedMethodChains(
        methods: List<ClassMethod>,
        allClasses: List<KotlinClass>
    ): List<MethodCallNode> {
        return emptyList()
    }

    override fun generateRemovedMethodChains(
        methods: List<ClassMethod>,
        allClasses: List<KotlinClass>
    ): List<MethodCallNode> {
        return emptyList()
    }


        private fun buildChain(
    method: ClassMethod,
    visited: MutableSet<String>
    ): MethodCallNode {

        // защита от циклов
        if (!visited.add(method.fullName)) {
            return MethodCallNode(
                fullName = method.fullName,
                function = method.function,
                updates = "",
                nextCalls = mutableListOf()
            )
        }

        val node = MethodCallNode(
            fullName = method.fullName,
            function = method.function,
            updates = "",
            nextCalls = mutableListOf()
        )

        val nextVisited = visited.toMutableSet()

        // 🔹 DIRECT: кого этот метод вызывает
        for (call in method.callRecords) {
            val calleeMethod =
                call.parentClass.functionCalls
                    .firstOrNull { it.fullName == call.name }
                    ?: continue

            node.nextCalls.add(
                buildChain(calleeMethod, nextVisited)
            )
        }

        // 🔹 REVERSE: кто вызывает этот метод
        for (reverse in method.reverseCallRecords) {
            val callerMethod =
                reverse.parentClass.functionCalls
                    .firstOrNull { it.fullName == reverse.name }
                    ?: continue

            node.nextCalls.add(
                buildChain(callerMethod, nextVisited)
            )
        }

        return node
    }
}