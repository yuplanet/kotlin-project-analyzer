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

            // ================= UPSTREAM =======================
            builder.appendLine("=== UPSTREAM (WHO CALLS THIS METHOD) ===")

            buildLinearChains(
                node = root,
                builder = builder,
                direction = CallDirection.UPSTREAM
            )

            builder.appendLine()

            // ================= DOWNSTREAM =======================
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
     * Рекурсивный обход дерева вызовов.
     *
     * UPSTREAM: A -> B -> C  (C вызван B, B вызван A)
     * DOWNSTREAM: C -> B -> A (A вызывает B, B вызывает C)
     */
    private fun buildLinearChains(
        node: MethodCallNode,
        builder: StringBuilder,
        direction: CallDirection,
        path: List<String> = emptyList()
    ) {
        // простая защита от зацикливания
        if (node.fullName in path) return

        val currentPath = path + node.fullName

        val nextNodes = when (direction) {
            CallDirection.UPSTREAM -> node.reverseCalls   // 🔼 кто вызывает метод
            CallDirection.DOWNSTREAM -> node.calls        // 🔽 кого вызывает метод
        }.filter { it.fullName.isNotBlank() }

        // тупик — печатаем путь
        if (nextNodes.isEmpty()) {
            builder.appendLine(currentPath.joinToString(" -> "))
            return
        }

        // иначе рекурсивно продолжаем
        nextNodes.forEach { child ->
            buildLinearChains(child, builder, direction, currentPath)
        }
    }

    private enum class CallDirection {
        UPSTREAM,      // кто вызывает метод
        DOWNSTREAM     // кого вызывает метод
    }
}
