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

    private fun buildLinearChains(
        node: MethodCallNode,
        builder: StringBuilder,
        direction: CallDirection,
        depth: Int = 0,
        visited: MutableSet<String> = mutableSetOf()
    ) {
        // защита от циклов
        if (!visited.add("${direction}_${node.fullName}_$depth")) return

        val indent = " ".repeat(depth * 4)

        // === комментарии по уровням ===
        val comment = when {
            depth == 0 ->
                "// ИЗМЕНЕННЫЙ МЕТОД — источник изменений"

            direction == CallDirection.UPSTREAM ->
                "// МОЖЕТ ИЗМЕНИТЬСЯ ПОВЕДЕНИЕ ВЫШЕ ПО ЦЕПОЧКЕ (этот метод вызывает измененный)"

            direction == CallDirection.DOWNSTREAM ->
                "// МОЖЕТ ПОТРЕБОВАТЬ КОРРЕКТИРОВКУ ИЗ-ЗА ИЗМЕНЕНИЙ (вызывается измененным методом)"

            else -> ""
        }

        // === вывод строки ===
        if (depth == 0) {
            builder.appendLine("${node.fullName}    $comment")
        } else {
            builder.appendLine("$indent└─ ${node.fullName}    $comment")
        }

        val nextNodes = when (direction) {
            CallDirection.UPSTREAM -> node.reverseCalls
            CallDirection.DOWNSTREAM -> node.calls
        }.filter { it.fullName.isNotBlank() }

        nextNodes.forEach { child ->
            buildLinearChains(child, builder, direction, depth + 1, visited)
        }
    }

    private enum class CallDirection {
        UPSTREAM,      // кто вызывает метод
        DOWNSTREAM     // кого вызывает метод
    }
}
