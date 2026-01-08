package org.example.core.linking

import org.example.core.interfaces.IDependencyChainBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.ObjectReference

class DependencyChainBuilder(private val searchEngine: IProjectSearchEngine): IDependencyChainBuilder {

    override fun generateChangedMethodChains(
        methods: List<ClassMethod>,
        allClasses: List<KotlinClass>
    ): List<MethodCallNode> {

        val nodes = methods.map { method ->
            collectCalls(method)
        }

        return nodes
    }

    fun getEmptyNode(method: ClassMethod): MethodCallNode {

        return MethodCallNode(
            fullName = method.fullName,
            function = method.function,
            updates = "",
        )
    }

    fun collectCalls(method: ClassMethod, node: MethodCallNode? = null): MethodCallNode {

        val currentNode = node ?: getEmptyNode(method)

        val count = currentNode.visitedFunctionHistory.count { it == method.fullName }
        if (count >= 10) {
            currentNode.updates = "max repetitions reached"
            return currentNode
        }

        currentNode.visitedFunctionHistory.add(method.fullName)

        for (call in method.callRecords) {

            val callClassMethods = searchEngine.findAllMethodByClassNameAndFullMethodName(call.referenceTargetName)

            for (callClassMethod in callClassMethods) {

                val childNode = collectCalls(callClassMethod, currentNode)
                currentNode.calls.add(childNode)

            }
        }

        var reverseCalls = method.reverseCallRecords
        reverseCalls += getSuperClassReverseCalls(method)

        for (call in method.reverseCallRecords) {

            val callClassMethods = searchEngine.findAllMethodByClassNameAndFullMethodName(call.referenceTargetName)

            for (callClassMethod in callClassMethods) {

                val childNode = collectCalls(callClassMethod, currentNode)

                currentNode.reverseCalls.add(childNode)

            }
        }
        return currentNode
    }

    fun getSuperClassReverseCalls(method: ClassMethod): MutableList<ObjectReference> {

        var reverseCalls = mutableListOf<ObjectReference>()

        val methods = searchEngine.findAllMethodByClassNameAndFullMethodName(method.fullName)

        for (method in methods) {
            reverseCalls += method.reverseCallRecords
        }

        return reverseCalls
    }
}