package org.example.data.symbol

// только класс
sealed class ObjectReference {
    abstract var name: String
    abstract var parentClass: KotlinClass
}