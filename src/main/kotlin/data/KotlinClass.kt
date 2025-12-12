package org.example.data

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class KotlinClass {

    var Class: KtClassOrObject

    var Path: String

    var Name: String

    var Fields: Map<Int, KtProperty> = mapOf()

    var FieldsReferences: MutableList<ParamReference> = mutableListOf()

    var Functions: Map<Int, KtNamedFunction> = mapOf()

    val FunctionCalls: MutableList<KotlinMethod> = mutableListOf()

    constructor(Class: KtClassOrObject, name: String, Path: String) {
        this.Class = Class
        this.Path = Path
        this.Name = name
    }

    //Interface
}