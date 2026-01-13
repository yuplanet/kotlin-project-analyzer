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
            val classFullName = filePath.substringBeforeLast(".kt")
            val className = cls.name ?: filePath.substringAfterLast("/").substringBeforeLast(".kt")

            val type = KtClassHelper.getEntityType(cls)
            val annotation = KtClassHelper.getAnnotations(cls)

            val ktClass = KotlinClass(
                ktClassObject = cls,
                path = filePath,
                name = className,
                fullName = classFullName,

                ktParameters = cls.primaryConstructorParameters,
                ktProperties = cls.declarations.filterIsInstance<KtProperty>(),
                ktFunctions = KtFileExtractor.getClassMethods(cls),
                ktClassObjectType = type,
                annotations = annotation,
                ktFile = ktFile
            )

            // Создаем property и parameter references
            ktClass.propertyReferences.addAll(ktClass.ktProperties.map {
                ClassProperty(it.name ?: "__no_name__", it.typeReference?.text ?: "_", it, ktClass)
            })
            ktClass.parameterReferences.addAll(ktClass.ktParameters.map {
                ClassParameter(it.name ?: "__no_name__", it.typeReference?.text ?: "_", it, ktClass)
            })

            // Создаем функции с параметрами
            ktClass.functionCalls.addAll(
                ktClass.ktFunctions.map {
                    ClassMethod(
                        name = it.name ?: "__no_name__",
                        fullName = KtFunctionHelper.getFullFunctionName(it),
                        returnType = it.typeReference?.text ?: "Unit",
                        function = it,
                        parameterTypeNames = it.valueParameters.map { p -> p.typeReference?.text ?: "_" },
                        parentClass = ktClass,
                        annotations = getFunctionAnnotations(it)
                    )
                }
            )
            kotlinClasses.add(ktClass)
        }

        return kotlinClasses
    }


    fun linkSuperClasses(allClasses: List<KotlinClass>) {
        val classesByName: Map<String, KotlinClass> =
            allClasses.associateBy { it.ktClassObject.name ?: "__anonymous__" }

        allClasses.forEach { it.superClasses.clear() }

        for (ktClass in allClasses) {
            for (entry in ktClass.ktClassObject.superTypeListEntries) {
                val typeName = when (entry) {
                    is KtSuperTypeEntry -> entry.typeReference?.text
                    is KtSuperTypeCallEntry -> entry.typeReference?.text
                    else -> null
                } ?: continue

                val parentClass = classesByName[typeName] ?: continue
                ktClass.superClasses.add(parentClass)
            }
        }
    }

    /**
     * аннотации методов
    * */
    fun getFunctionAnnotations(ktFunction: KtNamedFunction): List<String> {
        val result = mutableListOf<String>()

        // 1️⃣ Аннотации самой функции
        result += ktFunction.annotationEntries.map { it.text }

        // 2️⃣ Аннотации return-типа
        ktFunction.typeReference
            ?.annotationEntries
            ?.mapTo(result) { it.text }

        return result
    }


    /**
     * Строит обратные связи: для каждого класса заполняет список его наследников (subClasses)
     */
    fun linkSubClasses(allClasses: List<KotlinClass>) {

        // очищаем старые данные на всякий случай
        allClasses.forEach { it.subClasses.clear() }

        // для каждого класса пройдемся по его предкам
        for (ktClass in allClasses) {
            for (superClass in ktClass.superClasses) {

                if (superClass.subClasses.none { it.fullName == ktClass.fullName }) {
                    superClass.subClasses.add(ktClass)
                }
            }
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
        linkSubClasses(allClasses)
        return allClasses
    }
}