package org.example.data.symbol

// только класс
sealed class ObjectReference {

    abstract var referenceTargetName: String
    abstract var signature: String
    abstract var referenceTargetParentClass: KotlinClass
}