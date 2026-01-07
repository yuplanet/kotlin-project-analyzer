package org.example.core.linking

import org.example.core.interfaces.IDependencyChainBuilder
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.ObjectReference

class DependencyChainBuilder(): IDependencyChainBuilder {

    override fun generateChangedMethodChains(
        methods: List<ClassMethod>,
        allClasses: List<KotlinClass>
    ): List<MethodCallNode> {
        return methods.map { method ->
            buildChain(method)
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
        visited: MutableSet<String> = mutableSetOf()
    ): MethodCallNode {

        if (!visited.add(method.fullName)) {
            return MethodCallNode(
                fullName = method.fullName,
                function = method.function,
                updates = "cycle",
                calls = mutableListOf(),
                reverseCalls = mutableListOf()
            )
        }

        val node = MethodCallNode(
            fullName = method.fullName,
            function = method.function,
            updates = "",
            calls = mutableListOf(),
            reverseCalls = mutableListOf()
        )

        processDirectCalls(method, node, visited)
        processReverseCalls(method, node, visited)

        return node
    }

    private fun resolveMethodInHierarchy(
        kotlinClass: KotlinClass,
        methodFullName: String,
        visited: MutableSet<String> = mutableSetOf()
    ): ClassMethod? {

        if (!visited.add(kotlinClass.fullName)) return null

        kotlinClass.functionCalls.firstOrNull { extractSignature(it.fullName) == extractSignature(methodFullName) }
            ?.let { return it }

        for (superClass in kotlinClass.superClasses) {
            val resolved = resolveMethodInHierarchy(
                kotlinClass = superClass,
                methodFullName = methodFullName,
                visited = visited
            )
            if (resolved != null) return resolved
        }

        return null
    }

    private fun extractSignature(fullName: String): String {
        return fullName.substringAfter("::")
    }

    private fun processDirectCalls(
        method: ClassMethod,
        node: MethodCallNode,
        visited: MutableSet<String>
    ) {
        for (call in method.callRecords) {

            val calleeMethod = resolveMethodInHierarchy(
                kotlinClass = call.referenceTargetParentClass,
                methodFullName = call.referenceTargetName
            ) ?: continue

            val child = buildChain(calleeMethod, visited)
            node.calls.add(child)
        }
    }

    private fun processReverseCalls(
        method: ClassMethod,
        node: MethodCallNode,
        visited: MutableSet<String>
    ) {

        val callse = collectReverseCallsFromHierarchy(method)
        for (reverse in callse) {

            val callerMethod = resolveMethodInHierarchy(
                kotlinClass = reverse.referenceTargetParentClass,
                methodFullName = reverse.referenceTargetName
            ) ?: continue

            val child = buildChain(callerMethod, visited)
            node.reverseCalls.add(child)
        }
    }

    private fun collectReverseCallsFromHierarchy(
        method: ClassMethod
    ): List<ObjectReference> {

        var result = mutableListOf<ObjectReference>()
        result+=method.reverseCallRecords

        // идем по супер-классам
        for (superClass in method.parentClass.superClasses) {

            val sameMethodInSuper = superClass.functionCalls.firstOrNull {
                extractSignature(it.fullName) == extractSignature(method.fullName)    // если есть сигнатура
            } ?: continue

            result += sameMethodInSuper.reverseCallRecords
        }

        return result
    }
}