package org.example.mapping

import org.example.core.PsiExtractor
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import kotlin.collections.plus

object KotlinClassMapper {

    /**
     * Преобразует KtFile в список KotlinClass
     */
    fun mapKtFileToClasses(ktFile: KtFile): List<KotlinClass> {

        // Находим все классы/объекты/интерфейсы внутри файла
        val classes = ktFile.collectDescendantsOfType<KtClassOrObject>()

        val kotlinClasses = mutableListOf<KotlinClass>()

        for (cls in classes) {
            val filePath = ktFile.name
            val className = filePath.substringAfterLast("/").substringBeforeLast(".kt")

            val ktClass = KotlinClass(cls, filePath, className)

            // Берём все функции класса и companion object
            //1 functions
            val namedFunctions = PsiExtractor.getClassMethods(cls)
            val functions = namedFunctions.map {
                ClassMethod(
                    name = it.name ?: "__no_name__",
                    fullName = methodKey(it),
                    function = it,
                    parameters = buildParameterMap(it)
                )
            }.toMutableList()

            ktClass.functionCalls.addAll(functions)
            ktClass.functions = functions.associateBy { it.fullName }.mapValues { it.value.function }


            // мапи поля и параметры(то же самое что и поля) класса
            val properties = cls.declarations.filterIsInstance<KtProperty>()
            val parameters = cls.primaryConstructorParameters

            val fieldRefs = properties.map { ClassProperty(it) }
            val paramRefs = parameters.map { ClassParameter(it) }

            ktClass.propertyReferences.addAll(fieldRefs)
            ktClass.parameterReferences.addAll(paramRefs)

            ktClass.properties = properties.toMutableList()
            ktClass.parameters = parameters.toMutableList()

            kotlinClasses.add(ktClass)
        }

        return kotlinClasses
    }


    fun methodKey(fn: KtNamedFunction): String {
        // 1. Имя метода
        val name = fn.name ?: "__no_name__"

        // 2. Тип расширения (если extension-функция)
        val receiverType = fn.receiverTypeReference?.text?.let { "$it." } ?: ""

        // 3. Параметры метода
        val params = fn.valueParameters.joinToString(",") { it.typeReference?.text ?: "Any" }

        // 4. Имя класса, если есть
        val className = fn.getContainingClassName()

        // 5. Тип возвращаемого значения
        val returnType = fn.typeReference?.text ?: "Unit"

        return "$className::$receiverType$name($params):$returnType"
    }

    // Вспомогательная функция для получения имени класса или top-level
    fun KtNamedFunction.getContainingClassName(): String {
        var parent = this.parent
        while (parent != null) {
            if (parent is KtClassOrObject) {
                return parent.name ?: "__anonymous__"
            }
            parent = parent.parent
        }
        return "__top_level__"
    }

    fun buildParameterMap(fn: KtNamedFunction): Map<String, KtParameter> {
        return fn.valueParameters.associate { param ->
            val name = param.typeReference?.text ?: "Any"
            val type = param
            name to type
        }
    }


    /**
     * Преобразует список KtFile в список KotlinClass
     */
    fun mapKtFilesToClasses(ktFiles: List<KtFile>): List<KotlinClass> {

        val allClasses  = ktFiles.flatMap { ktFile ->
            mapKtFileToClasses(ktFile)
        }


        val classesByName: Map<String, KotlinClass> =
            allClasses.associateBy { it.ktClassObject.name ?: "__anonymous__" }

        // 4. Проставляем родителей (на всех уровнях)
        for (kclass in allClasses) {
            val visitedParents = mutableSetOf<KotlinClass>()
            fun collectParents(clsObj: KtClassOrObject) {
                clsObj.superTypeListEntries.forEach { entry ->
                    val typeName = when (entry) {
                        is KtSuperTypeEntry -> entry.typeReference?.text
                        is KtSuperTypeCallEntry -> entry.typeReference?.text
                        else -> null
                    } ?: return@forEach

                    val parentClass = classesByName[typeName] ?: return@forEach
                    if (visitedParents.add(parentClass)) {
                        kclass.superClasses = kclass.superClasses + parentClass
                        collectParents(parentClass.ktClassObject) // рекурсивно добавляем родителей
                    }
                }
            }
            collectParents(kclass.ktClassObject)
        }

        return allClasses
    }
}