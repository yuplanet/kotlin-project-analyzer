package org.example.core.utils

import org.example.data.symbol.enum.ObjectType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

object KtClassHelper {
    fun getParameters(cl : KtClassOrObject): List<KtParameter> = cl.primaryConstructorParameters
    fun getProperties(cl : KtClassOrObject): List<KtProperty> = cl.declarations.filterIsInstance<KtProperty>()

    fun getEntityType(cl : KtClassOrObject): ObjectType {
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

    fun getAnnotations(cl : KtClassOrObject): List<String> {
        return cl.annotationEntries.map { entry ->
            // Получаем текст аннотации, например "@Serializable"
            entry.shortName?.asString() ?: entry.text
        }
    }

    fun getNamedFunctions(klass: KtClassOrObject): MutableList<KtNamedFunction> {
        return klass.declarations
            .filterIsInstance<KtNamedFunction>()
            .toMutableList()
    }
}