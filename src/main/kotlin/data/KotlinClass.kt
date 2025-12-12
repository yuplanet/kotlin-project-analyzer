package org.example.data

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class KotlinClass {
    var KtClass: KtClassOrObject
    var Path: String
    var name: String

    var Fields: Map<Int, KtProperty> = mapOf()

    var FieldsReferences: MutableList<ParamReference> = mutableListOf()

    var Functions: Map<Int, KtNamedFunction> = mapOf()

    val FunctionCalls: MutableList<KotlinMethod> = mutableListOf()

    constructor(ktClass: KtClassOrObject, name: String, Path: String) {
        this.KtClass = ktClass
        this.Path = Path
        this.name = name
    }

    //Interface
}