package org.example.core.interfaces

import org.example.data.symbol.KotlinClass

interface IClassReferenceBuilder {

    fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine, branchName: String)
}