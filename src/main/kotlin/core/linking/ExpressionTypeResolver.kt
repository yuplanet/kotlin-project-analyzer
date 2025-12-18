package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FullExpression
import org.example.data.symbol.KotlinClass

object ExpressionTypeResolver {


    fun resolveExpressionType(
        expr: FullExpression,
        parentMethod: ClassMethod,
        parentClass: KotlinClass,
        engine: IProjectSearchEngine
    ) {
        // --- RESOLVE RECEIVER ---
        expr.type = when {
            expr.receiver == "this" -> parentClass.name // класс текущего метода
            else -> {
                // 1️⃣ Локальные свойства метода
                parentMethod.properties.firstOrNull { it.name == expr.receiver }?.type
                // 2️⃣ Параметры метода
                    ?: parentMethod.ktParameters.firstOrNull { it.name == expr.receiver }?.typeReference?.text
                    // 3️⃣ Поля класса
                    ?: parentClass.propertyReferences.firstOrNull { it.name == expr.receiver }?.type
                    // 4️⃣ Параметры класса
                    ?: parentClass.parameterReferences.firstOrNull { it.name == expr.receiver }?.type
                    // 5️⃣ Через движок
                    ?: engine.findByClassName(expr.receiver)?.name
                    // если не нашли
                    ?: "_"
            }
        }

        // --- RESOLVE PARAMS ---
        expr.params = expr.params.map { param ->
            when {
                param.contains(".") -> param.substringBefore(".") // EnumName.VALUE -> EnumName
                param.endsWith("()") -> param.substringBefore("(") // Constructor() -> Constructor
                else -> {
                    // 1️⃣ Локальные свойства метода
                    parentMethod.properties.firstOrNull { it.name == param }?.type
                    // 2️⃣ Параметры метода
                        ?: parentMethod.ktParameters.firstOrNull { it.name == param }?.typeReference?.text
                        // 3️⃣ Поля класса
                        ?: parentClass.propertyReferences.firstOrNull { it.name == param }?.type
                        // 4️⃣ Параметры класса
                        ?: parentClass.parameterReferences.firstOrNull { it.name == param }?.type
                        // 5️⃣ Через движок
                        ?: engine.findByClassName(param)?.name
                        // если не нашли
                        ?: "_"
                }
            }
        }.toMutableList()
    }

}