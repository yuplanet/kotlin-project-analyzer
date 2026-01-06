package org.example.core

import org.example.core.interfaces.IDiffResultPresenter
import org.example.data.analyzer.ProjectDiffResultOutput
import org.example.data.chain.MethodCallNode
import java.io.File

class DiffResultPresenter : IDiffResultPresenter {

    override fun writeCallChainToFile(result: ProjectDiffResultOutput) {
        val builder = StringBuilder()

        result.changedMethods.forEach { root ->

            builder.appendLine("=== METHOD ===")
            builder.appendLine(root.fullName)
            builder.appendLine()

            builder.appendLine("=== UPSTREAM (WHO CALLS THIS METHOD) ===")
            buildLinearChains(
                node = root,
                builder = builder,
                direction = CallDirection.UPSTREAM
            )
            builder.appendLine()

            builder.appendLine("=== DOWNSTREAM (WHAT THIS METHOD CALLS) ===")
            buildLinearChains(
                node = root,
                builder = builder,
                direction = CallDirection.DOWNSTREAM
            )

            builder.appendLine("\n----------------------------------------\n")
        }

        File("changed_methods.txt").writeText(builder.toString())
    }

    /**
     * Рекурсивно обходит граф вызовов и печатает все линейные цепочки
     *
     * UPSTREAM:   A -> B -> C (A вызывает B, B вызывает C)
     * DOWNSTREAM: C -> B -> A (C вызывает B, B вызывает A)
     */
    private fun buildLinearChains(
        node: MethodCallNode,
        builder: StringBuilder,
        direction: CallDirection,
        path: List<String> = emptyList()
    ) {
        // защита от циклов
        if (node.fullName in path) return

        val currentPath = path + node.fullName

        val nextNodes = when (direction) {
            CallDirection.UPSTREAM -> node.nextCalls
            CallDirection.DOWNSTREAM -> node.nextCalls
        }.filter { it.fullName.isNotBlank() }

        // если дальше идти некуда — печатаем цепочку
        if (nextNodes.isEmpty()) {
            builder.appendLine(currentPath.joinToString(" -> "))
            return
        }

        // иначе идём глубже
        nextNodes.forEach { child ->
            buildLinearChains(child, builder, direction, currentPath)
        }
    }

    private enum class CallDirection {
        UPSTREAM,    // кто вызывает метод
        DOWNSTREAM   // кого вызывает метод
    }
}
