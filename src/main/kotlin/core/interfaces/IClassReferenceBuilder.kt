package org.example.core.interfaces

import org.example.data.symbol.KotlinClass

interface IClassReferenceBuilder {

    fun bindAll(allClasses: List<KotlinClass>)
    fun bindMethodCalls(allClasses: List<KotlinClass>)
    fun bindPropertyCalls(allClasses: List<KotlinClass>)
    fun bindParameterCalls(allClasses: List<KotlinClass>)
}