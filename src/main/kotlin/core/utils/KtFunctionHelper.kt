package org.example.core.utils

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

object KtFunctionHelper {

    fun getFunctionParameterNames(fn: KtNamedFunction): List<String> {
        val paramsPsi: List<KtParameter> = fn.valueParameters
        val parameterTypes: List<String> = paramsPsi.map { it.typeReference?.text ?: "_" }
        return parameterTypes
    }

    fun getFullFunctionName(fn: KtNamedFunction): String {
        // 1. Имя метода
        val name = fn.name ?: "__no_name__"

        // 2. Тип расширения (если extension-функция)
        val receiverType = fn.receiverTypeReference?.text?.let { "$it." } ?: ""

        // 3. Параметры метода
        val params = fn.valueParameters.joinToString(",") { it.typeReference?.text ?: "Any" }

        // 4. Имя класса, если есть
        val className = getFunctionParentClassName(fn)

        // 5. Тип возвращаемого значения
        val returnType = fn.typeReference?.text ?: "Unit"

        return "$className::$receiverType$name($params):$returnType"
    }

    // Вспомогательная функция для получения имени класса или top-level
    fun getFunctionParentClassName(fn: KtNamedFunction): String {
        var parent = fn.parent
        while (parent != null) {
            if (parent is KtClassOrObject) {
                return parent.name ?: "__anonymous__"
            }
            parent = parent.parent
        }
        return "__top_level__"
    }
}