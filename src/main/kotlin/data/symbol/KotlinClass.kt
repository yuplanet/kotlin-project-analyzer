package org.example.data.symbol

import org.example.data.symbol.enum.ObjectType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

class KotlinClass(

    /**
     * path / name /.kt
     * src/main/kotlin/de/axxessio/a2sre/A2sreApplication.kt
     */
    var path: String,

    /**
     * path / name
     * src/main/kotlin/de/axxessio/a2sre/A2sreApplication
     */
    val fullName: String,

    /**
     *  name
     * /A2sreApplication
     */
    var name: String,
    var ktFile: KtFile,

    var ktClassObjectType: ObjectType,
    var annotations: List<String> = emptyList(),
    val ktClassObject: KtClassOrObject,


    var propertyReferences: MutableList<ClassProperty> = mutableListOf(),
    var parameterReferences: MutableList<ClassParameter> = mutableListOf(),
    val functionCalls: MutableList<ClassMethod> = mutableListOf(),

    var ktParameters: List<KtParameter> = emptyList(),
    var ktProperties: List<KtProperty> = emptyList(),
    var ktFunctions: List<KtNamedFunction> = emptyList(),

    var subClasses: MutableList<KotlinClass> = mutableListOf(),
    var superClasses: MutableList<KotlinClass> = mutableListOf(),
)