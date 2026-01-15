package org.example.core.linking

import org.example.core.interfaces.IDependencyChainBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.enum.ObjectType

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

    override fun generateApiCallChain(callChainList: List<MethodCallNode>): List<MethodCallNode> {
        val result = mutableListOf<MethodCallNode>()

        callChainList.forEach { startNode ->

            // вниз по calls
            collectToApiByCalls(
                node = startNode,
                result = result,
                visited = mutableSetOf()
            )

            // вверх по reverseCalls
            collectToApiByReverseCalls(
                node = startNode,
                result = result,
                visited = mutableSetOf()
            )
        }

        return result.distinctBy { it.fullName }
    }

    private fun collectToApiByCalls(
        node: MethodCallNode,
        result: MutableList<MethodCallNode>,
        visited: MutableSet<String>
    ): Boolean {

        if (!visited.add(node.fullName)) return false

        if(node.method.parentClass.ktClassObjectType == ObjectType.Interface)
            return false

        var isApiChain = node.method.isApi

        for (child in node.calls) {
            val isApi = collectToApiByCalls(child, result, visited)
            if (isApi)
                isApiChain = true
        }

        if (isApiChain)
            result.add(node)

        return isApiChain
    }

    private fun collectToApiByReverseCalls(
        node: MethodCallNode,
        result: MutableList<MethodCallNode>,
        visited: MutableSet<String>
    ): Boolean {

        if (!visited.add(node.fullName)) return false
        if(node.method.parentClass.ktClassObjectType == ObjectType.Interface)
            return false
        
        var isApiChain = node.method.isApi

        for (parent in node.reverseCalls) {
            if (collectToApiByReverseCalls(parent, result, visited)) {
                isApiChain = true
            }
        }

        if (isApiChain)
            result.add(node)

        return isApiChain
    }



    fun collectChains(method: ClassMethod): MethodCallNode {

        val node = MethodCallNode(
            fullName = method.fullName,
            function = method.function,
            updates = "",
            method = method
        )

        // запускаем отдельно
        collectDirectCalls(method, node)
        collectReverseCalls(method, node)

        return node
    }

    /**
     * ====== прямые вызовы ======
     */
    private fun collectDirectCalls(method: ClassMethod, node: MethodCallNode) {

        // защита от циклов
        if (node.visitedFunctionHistory.count { it == method.fullName } >= 100) {
            node.updates = "max direct repetitions reached"
            return
        }

        //интерфейсы нас не интересуют
        if(method.parentClass.ktClassObjectType == ObjectType.Interface)
            return

        node.visitedFunctionHistory.add(method.fullName)

        for (call in method.callRecords) {

            val target = searchEngine.findMethodByFullName(call.referenceTargetName)
                    ?: continue

            //skip interface
            if(target.parentClass.ktClassObjectType == ObjectType.Interface)
                continue

            val child = MethodCallNode(
                fullName = target.fullName,
                function = target.function,
                updates = "",
                method = target
            )

            // передаем историю дальше
            child.visitedFunctionHistory = node.visitedFunctionHistory.toMutableList()

            collectDirectCalls(target, child)

            node.calls.add(child)
        }
    }

    /**
     * ====== обратные вызовы ======
     */
    private fun collectReverseCalls(method: ClassMethod, node: MethodCallNode) {

        if (node.visitedReverseFunctionHistory.count { it == method.fullName } >= 100) {
            node.updates = "max reverse repetitions reached"
            return
        }

        if(method.parentClass.ktClassObjectType == ObjectType.Interface)
            return

        node.visitedReverseFunctionHistory.add(method.fullName)

        for (call in method.reverseCallRecords) {

            val target =searchEngine.findMethodByFullName(call.referenceTargetName)
                    ?: continue

            //skip interface
            if(target.parentClass.ktClassObjectType == ObjectType.Interface)
                continue

            val child = MethodCallNode(
                fullName = target.fullName,
                function = target.function,
                updates = "",
                method = target
            )

            child.visitedReverseFunctionHistory =
                node.visitedReverseFunctionHistory.toMutableList()

            collectReverseCalls(target, child)

            node.reverseCalls.add(child)
        }
    }
}