package org.example.core

import org.example.core.interfaces.i_diffResultPresenter
import org.example.data.CallChainNode
import java.io.File

class DiffResultPresenter : i_diffResultPresenter {

    override fun writeCallChainToFile(result: List<CallChainNode>) {
        val builder = StringBuilder()

        result.forEach { root ->
            buildLinearChains(root, builder)
            builder.appendLine() // пустая строка между цепями
        }
        File("changed_methods.txt").writeText(builder.toString())
    }

    /**
     * Рекурсивно обходит цепь и выводит все ветки в виде отдельных линейных цепочек
     * Root -> El1 -> El1Child1
     */
    private fun buildLinearChains(
        node: CallChainNode,
        builder: StringBuilder,
        visited: MutableSet<String> = mutableSetOf(),
        path: List<String> = emptyList()
    ) {
        if (!visited.add(node.fullName)) return

        // Формируем текущий путь
        val currentPath = path + node.fullName

        // Если нет дочерних вызовов — печатаем путь
        val validNext = node.nextCalls.filter { it.fullName.isNotBlank() }
        if (validNext.isEmpty()) {
            builder.appendLine(currentPath.joinToString(" -> "))
            return
        }

        // Рекурсивно для всех веток
        validNext.forEach { child ->
            buildLinearChains(child, builder, visited.toMutableSet(), currentPath)
        }
    }
}
