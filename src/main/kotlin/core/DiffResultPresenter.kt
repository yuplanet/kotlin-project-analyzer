package org.example.core

import org.example.core.interfaces.IDiffResultPresenter
import org.example.data.analyzer.ProjectDiffResultOutput
import org.example.data.chain.MethodCallNode
import java.io.File

class DiffResultPresenter : IDiffResultPresenter {

    override fun writeCallChainToFile(result: ProjectDiffResultOutput) {

        val treeLike = StringBuilder()
        val pathLike = StringBuilder()

        // ======= ВИД №1 (дерево с отступами) =======
        result.changedMethods.forEach { root ->

            treeLike.appendLine("=== METHOD ===")
            treeLike.appendLine(root.fullName)
            treeLike.appendLine()

            treeLike.appendLine("=== UPSTREAM (WHO CALLS THIS METHOD) ===")
            buildLinearChains(root, treeLike, CallDirection.UPSTREAM)

            treeLike.appendLine()

            treeLike.appendLine("=== DOWNSTREAM (WHAT THIS METHOD CALLS) ===")
            buildLinearChains(root, treeLike, CallDirection.DOWNSTREAM)

            treeLike.appendLine("\n----------------------------------------\n")
        }

        // ======= ВИД №2 (полные пути) =======
        result.changedMethods.forEach { root ->

            pathLike.appendLine("=== METHOD ===")
            pathLike.appendLine(root.fullName)
            pathLike.appendLine()

            pathLike.appendLine("=== UPSTREAM PATHS ===")
            buildLinearChains2(root, pathLike, CallDirection.UPSTREAM)

            pathLike.appendLine()

            pathLike.appendLine("=== DOWNSTREAM PATHS ===")
            buildLinearChains2(root, pathLike, CallDirection.DOWNSTREAM)

            pathLike.appendLine("\n----------------------------------------\n")
        }

        File("changed_methods_tree.txt").writeText(treeLike.toString())
        File("changed_methods_paths.txt").writeText(pathLike.toString())
    }


    private fun buildLinearChains(
        node: MethodCallNode,
        builder: StringBuilder,
        direction: CallDirection,
        depth: Int = 0,
        pathVisited: MutableSet<String> = mutableSetOf()
    ) {
        val key = "${direction}_${node.fullName}"
        if (key in pathVisited) {
            builder.appendLine(
                "${" ".repeat(depth * 4)}└─ ${node.fullName}    // ⟳ цикл"
            )
            return
        }

        pathVisited.add(key)

        val indent = " ".repeat(depth * 4)

        val comment = when {
            depth == 0 ->
                "// ИЗМЕНЕННЫЙ МЕТОД — источник изменений"

            direction == CallDirection.UPSTREAM ->
                "// МОЖЕТ ИЗМЕНИТЬСЯ ПОВЕДЕНИЕ ВЫШЕ ПО ЦЕПОЧКЕ"

            else ->
                "// МОЖЕТ ПОТРЕБОВАТЬ КОРРЕКТИРОВКУ"
        }

        if (depth == 0) {
            builder.appendLine("${node.fullName}    $comment")
        } else {
            builder.appendLine("$indent└─ ${node.fullName}    $comment")
        }

        val nextNodes = when (direction) {
            CallDirection.UPSTREAM -> node.reverseCalls
            CallDirection.DOWNSTREAM -> node.calls
        }

        nextNodes.forEach { child ->
            buildLinearChains(
                child,
                builder,
                direction,
                depth + 1,
                pathVisited.toMutableSet() // 🔥 ВАЖНО
            )
        }
    }


    private fun buildLinearChains2(
        node: MethodCallNode,
        builder: StringBuilder,
        direction: CallDirection,
        path: List<String> = emptyList(),
        printed: MutableSet<String> = mutableSetOf()
    ) {
        if (node.fullName in path) return

        val currentPath = path + node.fullName

        val nextNodes = when (direction) {
            CallDirection.UPSTREAM -> node.reverseCalls
            CallDirection.DOWNSTREAM -> node.calls
        }.filter { it.fullName.isNotBlank() }

        // тупик — печатаем путь
        if (nextNodes.isEmpty()) {
            val line = currentPath.joinToString(" -> ")

            // <<< УДАЛЕНИЕ ДУБЛИКАТОВ >>>
            if (printed.add(line)) {
                builder.appendLine(line)
            }

            return
        }

        nextNodes.forEach { child ->
            buildLinearChains2(child, builder, direction, currentPath, printed)
        }
    }

    private enum class CallDirection {
        UPSTREAM,      // кто вызывает метод
        DOWNSTREAM     // кого вызывает метод
    }
}
