package org.example.data.reference

import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodInfo

sealed  class ObjectReference {
    abstract var name: String
    abstract var parentClass: KotlinClass
}

data class MethodReference(
    override var name: String,
    override var parentClass: KotlinClass,

    val method: MethodInfo,
) : ObjectReference()

data class FieldReference(
    override var name: String,
    override var parentClass: KotlinClass


) : ObjectReference()

data class ParameterReference(
    override var name: String,
    override var parentClass: KotlinClass

) : ObjectReference()