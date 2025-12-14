package org.example.data

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

class KotlinClass {

    var ktClassObject: KtClassOrObject

    var implementedInterfaces: List<KtClassOrObject> = listOf()

    var path: String

    var fields: MutableList<KtProperty> = mutableListOf()
    var parameters: MutableList<KtParameter> = mutableListOf()

    var fieldReferences: MutableList<FieldReference> = mutableListOf()
    var parameterReferences: MutableList<ParamReference> = mutableListOf()

    var reverseFieldReferences: MutableList<FieldReference> = mutableListOf()
    var reverseParameterReferences: MutableList<ParamReference> = mutableListOf()

    var functions: Map<String, KtNamedFunction> = mapOf()

    val functionCalls: MutableList<KotlinMethod> = mutableListOf()

    constructor(ktClass: KtClassOrObject, path: String) {
        this.ktClassObject = ktClass
        this.path = path
    }

    //Interface
}