package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.search.KiFileIndexed
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FullExpression
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.VariableInfo
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

        val variables = collectClassVariables(parentClass)

        expr.params = expr.params.map { param ->
            val resolvedType = variables.firstOrNull { it.name == param.name }?.type
                ?: parentMethod.fullExpressions.firstOrNull { it.collingContext.name == param.name }?.collingContext?.type
                ?: when {
                    param.name.contains("(") -> {
                        // метод → ищем через engine с известными типами аргументов
                         val methodName = param.name.substringBefore("(")
                        val argumentTypes = expr.params.map { it.type } // уже известные типы других параметров
                        engine.findMethodByClassNameAndMethodNameAndParams(
                            className = expr.collingContext.name,
                            methodName = methodName,
                            params = argumentTypes
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
                        // системный тип через импорты
                        resolveTypeFromImports(param.name, ktFile) ?: "_"
                    }
                }
            param.copy(type = resolvedType)
        }.toMutableList()

        // --- 1️⃣ RESOLVE CALLING CONTEXT TYPE ---
        expr.collingContext.type =

            variables.firstOrNull { it.name == expr.collingContext.name }?.type
                ?: resolveTypeFromImports(expr.receiver, ktFile)
                        ?: "_"

                    ?: parentMethod.ktParameters.firstOrNull { it.name == expr.collingContext.name }?.typeReference?.text?.takeIf { it != "_" }

                    ?: parentClass.propertyReferences.firstOrNull { it.name == expr.collingContext.name }?.type?.takeIf { it != "_" }

                    ?: parentClass.parameterReferences.firstOrNull { it.name == expr.collingContext.name }?.type?.takeIf { it != "_" }

                    ?: engine.findByClassName(expr.receiver)?.name?.takeIf { it != "_" }

                    ?: resolveTypeFromImports(expr.receiver, ktFile)

                    ?: "_"

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


    fun collectClassVariables(klass: KotlinClass): List<VariableInfo> {
        val variables = mutableListOf<VariableInfo>()

        // 1️⃣ Свойства класса
        klass.propertyReferences.forEach { prop ->
            variables.add(VariableInfo(name = prop.name, type = prop.type))
        }

        // 2️⃣ Параметры конструктора
        klass.parameterReferences.forEach { param ->
            variables.add(VariableInfo(name = param.name, type = param.type))
        }

        return variables
    }
}