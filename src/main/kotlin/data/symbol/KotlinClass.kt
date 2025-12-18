package org.example.data.symbol

import org.example.data.symbol.enum.ObjectType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

class KotlinClass {

    val ktClassObject: KtClassOrObject
    var ktClassObjectType: ObjectType
    var annotations: List<String>

    var superClasses: MutableList<KotlinClass> = mutableListOf()
    var subClasses: MutableList<KotlinClass> = mutableListOf()

    var name: String
    var path: String
    var fullName: String

    var propertyReferences: MutableList<ClassProperty> = mutableListOf()
    var parameterReferences: MutableList<ClassParameter> = mutableListOf()

    val functionCalls: MutableList<ClassMethod> = mutableListOf()

    var ktProperties: MutableList<KtProperty>
    var ktParameters: MutableList<KtParameter>
    var ktFunctions: MutableList<KtNamedFunction>

    constructor(
        ktClass: KtClassOrObject,
        path: String,
        name: String,
        ktParameters: List<KtParameter>,
        ktProperties: List<KtProperty>,
        ktClassObjectType: ObjectType,
        annotations: List<String>,
        ktFunctions: List<KtNamedFunction>,
        ) {
        this.ktClassObject = ktClass
        this.fullName = path
        this.name = name
        this.path = path

        this.ktProperties = ktProperties.toMutableList()
        this.ktParameters = ktParameters.toMutableList()
        this.ktClassObjectType = ktClassObjectType
        this.annotations = annotations
        this.ktFunctions = ktFunctions.toMutableList()
    }
}