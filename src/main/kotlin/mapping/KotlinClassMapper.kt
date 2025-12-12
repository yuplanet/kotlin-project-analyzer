package org.example.mapping

import org.example.core.PsiExtractor
import org.example.data.KotlinClass
import org.example.data.KotlinMethod
import org.example.data.ParamReference
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object KotlinClassMapper {

    /**
     * Преобразует KtFile в список KotlinClass
     */
    fun mapKtFileToClasses(ktFile: KtFile): List<KotlinClass> {

        // Находим все классы/объекты/интерфейсы внутри файла
        val classes = ktFile.collectDescendantsOfType<KtClassOrObject>()

        val kotlinClasses = mutableListOf<KotlinClass>()

        for (cls in classes) {

            val kclass = KotlinClass(cls, ktFile.name, ktFile.name)

            // Берём все функции класса и companion object
            val functions = PsiExtractor.getClassMethods(cls)

            // Генерируем KotlinMethod с уникальными ID
            val methods = mutableListOf<KotlinMethod>()

            var currentId = 0
            for (fn in functions) {
                val method = KotlinMethod(
                    Id = currentId,
                    Function = fn
                )
                methods.add(method)
                currentId++
            }
            val fields = cls.declarations
                .filterIsInstance<KtProperty>()
                .toList()

            val paramRefs = mutableListOf<ParamReference>()
            currentId = 0
            for (field in fields) {
                val paramRef = ParamReference(
                    Id = currentId,
                    Property = field
                )
                paramRefs.add(paramRef)
                currentId++
            }

            kclass.FunctionCalls.addAll(methods)
            kclass.FieldsReferences.addAll(paramRefs)

            kclass.Functions = methods.associateBy { it.Id }.mapValues { it.value.Function }
            kclass.Fields = paramRefs.associateBy { it.Id }.mapValues { it.value.Property }

            kotlinClasses.add(kclass)
        }
        return kotlinClasses
    }


    /**
     * Преобразует список KtFile в список KotlinClass
     */
    fun mapKtFilesToClasses(ktFiles: List<KtFile>): List<KotlinClass> {
        return ktFiles.flatMap { ktFile ->
            mapKtFileToClasses(ktFile)
        }
    }
}