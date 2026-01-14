package org.example.core.linking

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.KotlinClass

class ClassReferenceBuilder (): IClassReferenceBuilder {

    private lateinit var searchEngine: IProjectSearchEngine

    override fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine) {

        this.searchEngine = searchEngine

        // process of expressions inside class
        collectExpressions(projectClasses)

        // process of collect calls from history
        collectCallsFromExpressions(projectClasses)
    }

    fun collectExpressions(projectClasses: List<KotlinClass>) {

        for (cls in projectClasses) {
            for (method in cls.functionCalls) {
                val expressionCollector = ExpressionChainBuilder(method, searchEngine);
                expressionCollector.collectAllExpressions()
            }
        }
    }

    fun collectCallsFromExpressions(projectClasses: List<KotlinClass>) {

        val callResolver = ExpressionCallResolver(searchEngine) //CallResolverWithLogs(searchEngine)//
        callResolver.collect(projectClasses)
    }
}