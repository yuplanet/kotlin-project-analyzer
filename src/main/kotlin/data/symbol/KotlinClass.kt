package org.example.data.symbol

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

class KotlinClass {

    var ktClassObject: KtClassOrObject

    var superClasses: List<KotlinClass> = listOf()

    var name: String
    var path: String
    var fullName: String

    var properties: MutableList<KtProperty> = mutableListOf()
    var parameters: MutableList<KtParameter> = mutableListOf()

    var propertyReferences: MutableList<ClassProperty> = mutableListOf()
    var parameterReferences: MutableList<ClassParameter> = mutableListOf()

    var functions: MutableList<KtNamedFunction> = mutableListOf()
    val functionCalls: MutableList<ClassMethod> = mutableListOf()

    constructor(ktClass: KtClassOrObject, path: String, name: String) {
        this.ktClassObject = ktClass
        this.fullName = path
        this.name = name
        this.path = path
    }
}