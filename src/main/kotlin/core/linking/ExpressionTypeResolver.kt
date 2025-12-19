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

    fun resolveVariableType(
        name: String,
        parentMethod: ClassMethod,
        parentClass: KotlinClass,
        engine: IProjectSearchEngine,
        ktFile: KtFile
    ): String {
        // 1️⃣ Проверяем в методе (property и параметр)
        val methodType = parentMethod.properties.firstOrNull { it.name == name }?.type
            ?: parentMethod.ktParameters.firstOrNull { it.name == name }?.typeReference?.text

        if (methodType != null) return methodType

        // 2️⃣ Проверяем в классе (property и параметр)
        val classType = parentClass.propertyReferences.firstOrNull { it.name == name }?.type
            ?: parentClass.parameterReferences.firstOrNull { it.name == name }?.type
        if (classType != null) return classType

        // 3️⃣ Проверяем в движке
        val engineClass = engine.findByClassName(name)
        if (engineClass != null) {
            // Если enum или поле своего класса — берём до точки
            if (engineClass.ktClassObjectType == ObjectType.EnumClass || engineClass.ktClassObjectType == ObjectType.Class) {
                return name.substringBefore(".")
            }
            return engineClass.name
        }

        // 4️⃣ Проверяем системные импорты
        val importedType = resolveTypeFromImports(name, ktFile)
        if (importedType != null) return importedType

        // 5️⃣ Не нашли — возвращаем _
        return "_"
    }

    fun resolveParam(
        param: String,
        parentMethod: ClassMethod,
        parentClass: KotlinClass,
        engine: IProjectSearchEngine,
        ktFile: KtFile
    ): String {

        // 2️⃣ Enum или Object
        if (param.contains(".")) {
            val receiverName = param.substringBefore(".")
            val engineClass = engine.findByClassName(receiverName)
            if (engineClass != null) {
                return when (engineClass.ktClassObjectType) {
                    ObjectType.EnumClass -> receiverName // EnumName.VALUE -> EnumName
                    ObjectType.Class -> {
                        // ищем property с таким именем в классе
                        engineClass.propertyReferences.firstOrNull { it.name == param.substringAfter(".") }?.type
                            ?: "_" // если не нашли, оставляем "_"
                    }
                    else -> engineClass.name
                }
            }
            return receiverName
        }

        // 3️⃣ Обычная переменная
        return ExpressionTypeResolver.resolveVariableType(param, parentMethod, parentClass, engine, ktFile)
    }



    fun resolveExpressionType(
        expr: FullExpression,
        parentMethod: ClassMethod,
        parentClass: KotlinClass,
        engine: IProjectSearchEngine,
    ) {
        val ktFile = KiFileIndexed.getFileByClassName(parentClass.name) ?: return

        expr.collingContextType = if (expr.receiver == "this") parentClass.name
        else resolveVariableType(expr.receiver, parentMethod, parentClass, engine, ktFile)

        // --- RESOLVE PARAMS СНАЧАЛА ---
        expr.params = expr.params.map { param ->
            when {
                param.contains(")") -> param.substringBefore("(")
                param.contains(".") -> param.substringBefore(".")
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

        // --- RESOLVE VARIABLE TYPE ПОСЛЕ ПАРАМЕТРОВ ---
        expr.collingContextType = when {
            expr.receiver == "this" -> parentClass.name
            else -> {
                // если receiver совпадает с именем переменной, то ищем через движок, так как параметры уже резолвились
                parentMethod.properties.firstOrNull { it.name == expr.receiver }?.type
                    ?: parentMethod.ktParameters.firstOrNull { it.name == expr.receiver }?.typeReference?.text
                    ?: parentClass.propertyReferences.firstOrNull { it.name == expr.receiver }?.type
                    ?: parentClass.parameterReferences.firstOrNull { it.name == expr.receiver }?.type
                    ?: engine.findMethodByClassNameAndMethodNameAndParams(
                        className = expr.receiver,
                        methodName = expr.method,
                        params = expr.params
                    )?.parameterTypeNames?.lastOrNull() // берем возвращаемый тип метода
                    ?: engine.findByClassName(expr.receiver)?.name
                    ?: resolveTypeFromImports(expr.receiver, ktFile)
                    ?: "_"
            }
        }

        // --- ОБНОВЛЯЕМ ВСЕ FullExpression В parentMethod.fullExpressions ---
        parentMethod.fullExpressions.forEach { otherExpr ->
            // Обновляем тип переменной
            if (otherExpr.collingContext == expr.collingContext && otherExpr.collingContext.isNotEmpty()) {
                otherExpr.collingContextType = expr.collingContextType
            }

            // Обновляем параметры, если встречается имя текущей переменной
            otherExpr.params = otherExpr.params.map { p ->
                if (p == expr.collingContext) expr.collingContextType else p
            }.toMutableList()
        }


    }


}