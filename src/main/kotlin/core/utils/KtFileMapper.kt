package org.example.core.utils

import org.example.core.psi.KtFileExtractor
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.enum.ObjectType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object KtFileMapper {

    fun getFullFunctionName(fn: KtNamedFunction): String {
        // 1. Имя метода
        val name = fn.name ?: "__no_name__"

        // 2. Тип расширения (если extension-функция)
        val receiverType = fn.receiverTypeReference?.text?.let { "$it." } ?: ""

        // 3. Параметры метода
        val params = fn.valueParameters.joinToString(",") { it.typeReference?.text ?: "Any" }

        // 4. Имя класса, если есть
        val className = getFunctionParentClassName(fn)

        // 5. Тип возвращаемого значения
        val returnType = fn.typeReference?.text ?: "Unit"

        return "$className::$receiverType$name($params):$returnType"
    }

    // Вспомогательная функция для получения имени класса или top-level
    fun getFunctionParentClassName(fn: KtNamedFunction): String {
        var parent = fn.parent
        while (parent != null) {
            if (parent is KtClassOrObject) {
                return parent.name ?: "__anonymous__"
            }
            parent = parent.parent
        }
        return "__top_level__"
    }

    fun getEntityType(cl: KtClassOrObject): ObjectType {
        return when (cl) {
            is KtClass -> when {
                cl.isInterface() -> ObjectType.Interface
                cl.isEnum() -> ObjectType.EnumClass
                cl.isAnnotation() -> ObjectType.Undefined // или добавить Annotation в enum
                cl.isData() -> ObjectType.DataClass
                cl.isSealed() -> ObjectType.SealedClass
                else -> ObjectType.Class
            }

            is KtObjectDeclaration -> if (cl.isCompanion()) ObjectType.Undefined // можно добавить CompanionObject в enum
            else ObjectType.Object

            else -> ObjectType.Undefined
        }
    }

    fun getAnnotations(cl: KtClassOrObject): List<String> {
        return cl.annotationEntries.map { entry ->
            // Получаем текст аннотации, например "@Serializable"
            entry.shortName?.asString() ?: entry.text
        }
    }

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

            val type = getEntityType(cls)
            val annotation = getAnnotations(cls)

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

                val property = ClassProperty(
                    name = it.name ?: "__no_name__",
                    property = it,
                    parentClass = ktClass,
                    it.typeReference?.text ?: "_"
                )
                property
            })

            ktClass.parameterReferences.addAll(ktClass.ktParameters.map {
                val parameter = ClassParameter(
                    name = it.name ?: "__no_name__",
                    property = it,
                    parentClass = ktClass,
                    it.typeReference?.text ?: "_"
                )
                parameter
            })

            // Создаем функции с параметрами
            ktClass.functionCalls.addAll(
                ktClass.ktFunctions.map {
                    val method = ClassMethod(
                        name = it.name ?: "__no_name__",
                        fullName = getFullFunctionName(it),
                        returnType = it.typeReference?.text ?: "Unit",
                        function = it,
                        parameterTypeNames = it.valueParameters.map { p -> p.typeReference?.text ?: "_" },
                        parentClass = ktClass,
                        annotations = getFunctionAnnotations(it)
                    )

                    method
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