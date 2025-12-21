package org.example.core.linking

import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FullExpression
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.VariableInfo
import org.example.data.symbol.enum.ObjectType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

class ExpressionTypeResolver(private val searchEngine: IProjectSearchEngine) {

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
        val ktFile = searchEngine.getKtFileByClassName(parentClass.name)

        ktFile ?: return

        val variables = collectClassVariables(parentClass)

        //сначала разбираем методы. типа Class.Field, enum/item
        expr.params = expr.params.map { param ->


            val resolvedType = variables.firstOrNull { it.name == param.name }?.type
                ?: parentMethod.fullExpressions.firstOrNull { it.collingContext.name == param.name }?.collingContext?.type
                ?:

                when {
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
        expr.collingContext.type =  resolveExpressionCallingContext(variables, expr, ktFile, parentClass, parentMethod)

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



    // ищем тип вызвавший метод - a =fun() ищем тип а
    private fun resolveExpressionCallingContext(variables: List<VariableInfo>, expr: FullExpression, ktFile: KtFile, parentClass: KotlinClass, parentMethod: ClassMethod):String {

        var resolvedType: String? = null

        // 1. Проверяем локальные переменные
        val variable = variables.firstOrNull { it.name == expr.collingContext.name }
        if (variable != null && variable.type.isNotEmpty() && variable.type!="_")
            resolvedType = variable.type

        // 2. Если не нашли, проверяем импорты
        if (resolvedType == null)
            resolvedType = resolveTypeFromImports(expr.receiver, ktFile)

        // 3. fallback "_"
        if (resolvedType == null || resolvedType == "_") {
            // 4. Проверяем параметры метода
            val paramType = parentMethod.ktParameters
                .firstOrNull { it.name == expr.collingContext.name }
                ?.typeReference?.text
                ?.takeIf { it != "_" }

            if (paramType != null) resolvedType = paramType
        }

        // 5. Проверяем свойства класса
        if (resolvedType == null) {
            val propType = parentClass.propertyReferences
                .firstOrNull { it.name == expr.collingContext.name }
                ?.type?.takeIf { it != "_" }
            if (propType != null) resolvedType = propType
        }

        // 6. Проверяем параметры класса
        if (resolvedType == null) {
            val classParamType = parentClass.parameterReferences
                .firstOrNull { it.name == expr.collingContext.name }
                ?.type?.takeIf { it != "_" }
            if (classParamType != null) resolvedType = classParamType
        }

        // 7. Проверяем через движок по имени класса
        if (resolvedType == null)
            resolvedType = searchEngine.findByClassName(expr.receiver)?.name

        // 8. Ещё раз проверяем импорты (на всякий случай)
        if (resolvedType == null)
            resolvedType = resolveTypeFromImports(expr.receiver, ktFile)

        // Присваиваем
        return resolvedType?: "_"
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