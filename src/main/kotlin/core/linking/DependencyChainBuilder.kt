package org.example.core.linking

import org.example.core.interfaces.IDependencyChainBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.chain.MethodCallNode
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass

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


    fun collectCalls(method: ClassMethod, visited: MutableSet<String> = mutableSetOf()): MethodCallNode {
        val node = MethodCallNode(
            fullName = method.fullName,
            function = method.function,
            updates = "",
        )

        if (!visited.add(method.fullName)) {
            node.updates = "cycle"
            return node
        }

        for (call in method.callRecords) {

            val callClassMethods = getMethodFromSuperClass(call.referenceTargetName)

            for (callClassMethod in callClassMethods) {

                val childNode = collectCalls(callClassMethod, visited)
                node.calls.add(childNode)
            }
        }

        return node
    }

    fun getMethodFromSuperClass(methodFullName: String): MutableList<ClassMethod> {

        val calls = mutableListOf<ClassMethod>()

        //call inside method
        val methodCLassName = methodFullName.substringBefore("::")

        val fullMethod = methodFullName.substringAfter("::")

        val method = searchEngine.findMethodByClassNameAndFullMethodName(methodCLassName, fullMethod)
        method?.let { calls.add(it) }


        //call in super classes
        val mClass = searchEngine.findByClassName(methodCLassName)
        if (mClass != null) {

            for (superClass in mClass.superClasses) {

                val parentMethod = searchEngine.findMethodByClassNameAndFullMethodName(superClass.name, fullMethod)
                parentMethod?.let { calls.add(it) }
            }

            for (subClass in mClass.subClasses) {

                val subClassMethod = searchEngine.findMethodByClassNameAndFullMethodName(subClass.name, fullMethod)
                subClassMethod?.let { calls.add(it) }
            }
        }

        return calls
    }
}