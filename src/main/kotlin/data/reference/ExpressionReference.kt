package org.example.data.reference

import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodInfo
import org.example.data.symbol.VariableInfo


// только класс
sealed  class ObjectReference {
    abstract var name: String
    abstract var parentClass: KotlinClass
}

data class MethodReference(
    override var name: String,
    override var parentClass: KotlinClass,

    val method: MethodInfo, // по нему можем найти метод
) : ObjectReference()


data class FieldReference(
    override var name: String,
    override var parentClass: KotlinClass,

    var field: VariableInfo,
) : ObjectReference()

data class ClassReference(
    override var name: String,
    override var parentClass: KotlinClass

) : ObjectReference()