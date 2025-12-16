package org.example.mapping

import org.example.core.PsiExtractor
import org.example.data.ClassField
import org.example.data.KotlinClass
import org.example.data.KotlinMethod
import org.example.data.ClassProperty
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
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
            val filePath = ktFile.virtualFile?.path ?: ktFile.name

            val kclass = KotlinClass(cls, filePath)

            // Берём все функции класса и companion object
            val functions = PsiExtractor.getClassMethods(cls)

            val methods = functions.map { fn ->
                KotlinMethod(
                    fullName = methodKey(fn),
                    function = fn
                )
            }.toMutableList()

            val fields = cls.declarations.filterIsInstance<KtProperty>()
            val params = cls.primaryConstructorParameters

            val fieldRefs = fields.map {ClassField(it)}
            val paramRefs = params.map { ClassProperty(it) }

            kclass.functionCalls.addAll(methods)

            kclass.classFields.addAll(fieldRefs)
            kclass.parameterReferences.addAll(paramRefs)

            kclass.functions = methods.associateBy { it.fullName }.mapValues { it.value.function }
            kclass.fields = fields.toMutableList()
            kclass.parameters = params.toMutableList()

            kotlinClasses.add(kclass)
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

        return "$className::$receiverType$name($params)"
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