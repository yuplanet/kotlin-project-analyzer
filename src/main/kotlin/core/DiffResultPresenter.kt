package org.example.core

import org.example.core.interfaces.IDiffResultPresenter
import org.example.data.chain.MethodCallNode
import java.io.File

class DiffResultPresenter : IDiffResultPresenter {

    override fun writeCallChainToFile(result: List<MethodCallNode>) {
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
        node: MethodCallNode,
        builder: StringBuilder,
        path: List<String> = emptyList()
    ) {
        // Проверка на цикл в текущей ветке
        if (node.fullName in path) return

        // Формируем текущий путь
        val currentPath = path + node.fullName

        // Если нет дочерних вызовов — печатаем путь
        val validNext = node.nextCalls.filter { it.fullName.isNotBlank() }
        if (validNext.isEmpty()) {
            builder.appendLine(currentPath.joinToString(" -> "))
            return
        }

        // Рекурсивно обходим всех дочерних вызовов
        validNext.forEach { child ->
            buildLinearChains(child, builder, currentPath)
        }
    }
}
