package org.example.core.interfaces

import org.example.data.symbol.KotlinClass

interface IClassReferenceBuilder {

    fun initialize(allClasses: List<KotlinClass>)
    fun bindAll()
    fun bindMethodCalls()
    fun bindFieldCalls()
    fun bindParameterCalls()
}