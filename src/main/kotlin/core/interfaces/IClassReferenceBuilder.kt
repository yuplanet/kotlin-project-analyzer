package org.example.core.interfaces

interface IClassReferenceBuilder {

    fun init(searchEngine: IProjectSearchEngine)

    fun collectExpressions()

    fun collectCallsFromExpressions()
}