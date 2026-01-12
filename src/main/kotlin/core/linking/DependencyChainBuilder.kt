package org.example.core.linking

import org.example.core.interfaces.IDependencyChainBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass

class DependencyChainBuilder(
    private val searchEngine: IProjectSearchEngine
) : IDependencyChainBuilder {

    override fun generateChangedMethodChains(
        methods: List<ClassMethod>,
        allClasses: List<KotlinClass>
    ): List<MethodCallNode> =
        methods.map { method ->
            collectChains(method)
        }

    /**
     * Создает корневой узел и запускает рекурсивный сбор цепочек
     */
    fun collectChains(
        method: ClassMethod,
        depth: Int = 0,
        visited: MutableSet<String> = mutableSetOf()
    ): MethodCallNode {

        val node = MethodCallNode(
            fullName = method.fullName,
            function = method.function,
            updates = ""
        )

        // защита от рекурсии и циклов
        if (depth > 50) {
            node.updates = "max depth reached"
            return node
        }

        if (!visited.add(method.fullName)) {
            node.updates = "cycle detected"
            return node
        }

        // ==== прямые вызовы ====
        for (call in method.callRecords) {
            val target = searchEngine
                .findMethodByFullMethodExpression(call.referenceTargetName)
                ?: continue

            val child = collectChains(
                method = target,
                depth = depth + 1,
                visited = visited.toMutableSet() // копия!
            )

            node.calls.add(child)
        }

        // ==== обратные вызовы ====
        for (call in method.reverseCallRecords) {
            val target = searchEngine
                .findMethodByFullMethodExpression(call.referenceTargetName)
                ?: continue

            val child = collectChains(
                method = target,
                depth = depth + 1,
                visited = visited.toMutableSet()
            )

            node.reverseCalls.add(child)
        }

        return node
    }
}
