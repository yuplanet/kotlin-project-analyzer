package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.search.KiFileIndexed
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FullExpression
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.enum.ObjectType
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
        val ktFile = KiFileIndexed.getFileByClassName(parentClass.name) ?: return

        // --- 1️⃣ RESOLVE CALLING CONTEXT TYPE ---
        expr.collingContext.type =

                    parentMethod.properties.firstOrNull { it.name == expr.collingContext.name }?.type?.takeIf { it != "_" }

                    ?: parentMethod.ktParameters.firstOrNull { it.name == expr.collingContext.name }?.typeReference?.text?.takeIf { it != "_" }

                    ?: parentClass.propertyReferences.firstOrNull { it.name == expr.collingContext.name }?.type?.takeIf { it != "_" }

                    ?: parentClass.parameterReferences.firstOrNull { it.name == expr.collingContext.name }?.type?.takeIf { it != "_" }

                    ?: engine.findByClassName(expr.receiver)?.name?.takeIf { it != "_" }

                    ?: resolveTypeFromImports(expr.receiver, ktFile)

                    ?: "_"

        // --- 2️⃣ RESOLVE PARAMS ---
        expr.params = expr.params.map { param ->
            val resolvedType = when {

                param.name.contains("(") -> {
                    // метод → ищем через engine
                    val methodName = param.name.substringBefore("(")
                    engine.findMethodByClassNameAndMethodNameAndParams(
                        className = expr.collingContext.name,
                        methodName = methodName,
                        params = listOf() // TODO: можно расширить для точных параметров
                    )?.parameterTypeNames?.lastOrNull() ?: "_"
                }

                param.name.contains(".") -> {
                    // enum или object
                    val receiver = param.name.substringBefore(".")
                    val member = param.name.substringAfter(".")
                    val engineClass = engine.findByClassName(receiver)
                    when (engineClass?.ktClassObjectType) {
                        ObjectType.EnumClass -> receiver
                        ObjectType.Class -> engineClass.propertyReferences.firstOrNull { it.name == member }?.type ?: "_"
                        else -> engineClass?.name ?: "_"
                    }
                }
                else -> {
                    // обычная переменная
                    parentMethod.properties.firstOrNull { it.name == param.name }?.type
                        ?: parentMethod.ktParameters.firstOrNull { it.name == param.name }?.typeReference?.text
                        ?: parentClass.propertyReferences.firstOrNull { it.name == param.name }?.type
                        ?: parentClass.parameterReferences.firstOrNull { it.name == param.name }?.type
                        ?: engine.findByClassName(param.name)?.name
                        ?: resolveTypeFromImports(param.name, ktFile)
                        ?: "_"
                }
            }
            param.copy(type = resolvedType) // создаем новый VariableInfo с типом
        }.toMutableList()

        // --- 3️⃣ ОБНОВЛЕНИЕ ВСЕХ FullExpression В МЕТОДЕ ---
        parentMethod.fullExpressions.forEach { otherExpr ->
            if (otherExpr.collingContext.name == expr.collingContext.name && otherExpr.collingContext.name.isNotEmpty()) {
                otherExpr.collingContext.type = expr.collingContext.type
            }

            otherExpr.params = otherExpr.params.map { p ->
                if (p.name == expr.collingContext.name) p.copy(type = expr.collingContext.type) else p
            }.toMutableList()
        }
    }
}