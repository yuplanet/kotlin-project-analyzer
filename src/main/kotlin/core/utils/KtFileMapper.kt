package org.example.core.utils

import org.example.core.psi.KtFileExtractor
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.KotlinClass
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object KtFileMapper {

    /**
     * Преобразует KtFile в список KotlinClass
     */
    fun mapKtFileToClassList(ktFile: KtFile): List<KotlinClass> {

        val classes = ktFile.collectDescendantsOfType<KtClassOrObject>()
        val kotlinClasses = mutableListOf<KotlinClass>()

        for (cls in classes) {
            val filePath = ktFile.name
            val className = cls.name ?: filePath.substringAfterLast("/").substringBeforeLast(".kt")
            val type = KtClassHelper.getEntityType(cls)
            val annotation = KtClassHelper.getAnnotations(cls)

            val ktClass = KotlinClass(
                ktClass = cls,
                path = filePath,
                name = className,
                ktParameters = cls.primaryConstructorParameters,
                ktProperties = cls.declarations.filterIsInstance<KtProperty>(),
                ktFunctions = KtFileExtractor.getClassMethods(cls),
                ktClassObjectType = type,
                annotations = annotation,
                ktFile = ktFile
            )

            // Создаем property и parameter references
            ktClass.propertyReferences.addAll(ktClass.ktProperties.map {
                ClassProperty(it.name ?: "__no_name__", it.typeReference?.text ?: "_", it)
            })
            ktClass.parameterReferences.addAll(ktClass.ktParameters.map {
                ClassParameter(it.name ?: "__no_name__", it.typeReference?.text ?: "_", it)
            })

            // Создаем функции с параметрами
            ktClass.functionCalls.addAll(
                ktClass.ktFunctions.map {
                    ClassMethod(
                        name = it.name ?: "__no_name__",
                        fullName = KtFunctionHelper.getFullFunctionName(it),
                        returnType = it.typeReference?.text ?: "Unit",
                        function = it,
                        parameterTypeNames = it.valueParameters.map { p -> p.typeReference?.text ?: "_" }
                    )
                }
            )

            kotlinClasses.add(ktClass)
        }

        return kotlinClasses
    }


    fun linkSuperClasses(
        allClasses: List<KotlinClass>
    ) {
        val classesByName: Map<String, KotlinClass> =
            allClasses.associateBy { it.ktClassObject.name ?: "__anonymous__" }

        for (ktClass in allClasses) {

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
                        ktClass.superClasses.add(parentClass)
                        collectParents(parentClass.ktClassObject)
                    }
                }
            }

            collectParents(ktClass.ktClassObject)
        }
    }


    /**
     * Преобразует список KtFile в список KotlinClass
     */
    fun mapKtFilesToClassList(ktFiles: List<KtFile>): List<KotlinClass> {

        val allClasses = ktFiles.flatMap { ktFile ->
            mapKtFileToClassList(ktFile)
        }

        linkSuperClasses(allClasses)

        return allClasses
    }


    fun collectClassesByFile(ktFiles: List<KtFile>): Map<KtFile, List<KotlinClass>> {

       val mapKtFilesAndClasses = ktFiles.associateWith { ktFile ->
            mapKtFileToClassList(ktFile)
        }

        val allClasses = mapKtFilesAndClasses.values.flatten()

        linkSuperClasses(allClasses)

        return mapKtFilesAndClasses
    }
}