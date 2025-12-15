package org.example.core

import org.example.core.interfaces.i_diffResultPresenter
import org.example.data.CallChainNode
import java.io.File

class DiffResultPresenter : i_diffResultPresenter {

    override fun writeCallChainToFile(result: List<CallChainNode>) {
        val builder = StringBuilder()

        result.forEach { root ->
            buildLinearChain(root, builder)
            builder.appendLine() // пустая строка между цепями
        }

        File("changed_methods.txt").writeText(builder.toString())
    }

    /**
     * Строит линейный вывод цепочки: Root -> El1 -> El1Child1 -> ...
     */
    private fun buildLinearChain(
        node: CallChainNode,
        builder: StringBuilder,
        visited: MutableSet<String> = mutableSetOf()
    ) {
        if (!visited.add(node.fullName)) return

        builder.append(node.fullName)

        node.nextCalls.forEach { child ->
            builder.append(" -> ")
            buildLinearChain(child, builder, visited)
        }
    }
}