package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.search.KiFileIndexed
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FullExpression
import org.example.data.symbol.KotlinClass
import org.jetbrains.kotlin.psi.KtFile

object ExpressionTypeResolver {
    // Функция для резолва полного имени класса через импорты
    private fun resolveTypeFromImports(typeName: String, ktFile: KtFile): String? {
        // Сначала ищем точное совпадение
        ktFile.importDirectives.firstOrNull { it.importedFqName?.shortName()?.asString() == typeName }
            ?.importedFqName?.asString()?.let { return it }

        // Затем ищем wildcard import, например java.time.*
        ktFile.importDirectives.firstOrNull { it.importedFqName?.asString()?.endsWith(".*") == true }
            ?.importedFqName?.asString()?.removeSuffix(".*")?.let { pkg ->
                return "$pkg.$typeName"
            }

        return null
    }

    fun resolveExpressionType(
        expr: FullExpression,
        parentMethod: ClassMethod,
        parentClass: KotlinClass,
        engine: IProjectSearchEngine,
    ) {


        val ktFile = KiFileIndexed.getFileByClassName(parentClass.name)?:return
        // --- RESOLVE RECEIVER ---
        expr.type = when {
            expr.receiver == "this" -> parentClass.name // класс текущего метода
            else -> {
                parentMethod.properties.firstOrNull { it.name == expr.receiver }?.type
                    ?: parentMethod.ktParameters.firstOrNull { it.name == expr.receiver }?.typeReference?.text
                    ?: parentClass.propertyReferences.firstOrNull { it.name == expr.receiver }?.type
                    ?: parentClass.parameterReferences.firstOrNull { it.name == expr.receiver }?.type
                    ?: engine.findByClassName(expr.receiver)?.name
                    ?: resolveTypeFromImports(expr.receiver, ktFile)
                    ?: "_"
            }
        }

        // --- RESOLVE PARAMS ---
        expr.params = expr.params.map { param ->
            when {
                param.contains(".") -> param.substringBefore(".") // EnumName.VALUE -> EnumName
                param.endsWith("()") -> param.substringBefore("(") // Constructor() -> Constructor
                else -> {
                    parentMethod.properties.firstOrNull { it.name == param }?.type
                        ?: parentMethod.ktParameters.firstOrNull { it.name == param }?.typeReference?.text
                        ?: parentClass.propertyReferences.firstOrNull { it.name == param }?.type
                        ?: parentClass.parameterReferences.firstOrNull { it.name == param }?.type
                        ?: engine.findByClassName(param)?.name
                        ?: resolveTypeFromImports(param, ktFile)
                        ?: "_"
                }
            }
        }.toMutableList()
    }
}