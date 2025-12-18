package org.example.core.interfaces

import org.example.data.symbol.KotlinClass

interface IClassReferenceBuilder {

    fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine)

    fun bindMethodCalls(projectClasses: List<KotlinClass>)

    fun bindPropertyCalls(projectClasses: List<KotlinClass>)

    fun bindParameterCalls(projectClasses: List<KotlinClass>)
}