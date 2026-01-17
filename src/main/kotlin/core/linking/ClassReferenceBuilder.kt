package org.example.core.linking

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.data.symbol.KotlinClass

class ClassReferenceBuilder ( ): IClassReferenceBuilder {

    private lateinit var searchEngine: IProjectSearchEngine
    private lateinit var projectClasses: List<KotlinClass>

    override fun init(searchEngine: IProjectSearchEngine) {
        this.searchEngine = searchEngine
        this.projectClasses = searchEngine.getAllClasses()
    }

    override fun collectExpressions() {

        val expressionCollector = ExpressionChainBuilder(searchEngine);

        for (cls in projectClasses) {
            for (method in cls.functionCalls) {
                expressionCollector.collectAllExpressions(method)
            }
        }

        searchEngine.updateMethodsHashes()
    }

    override fun collectCallsFromExpressions() {
        val callResolver = ExpressionCallResolver(searchEngine) //CallResolverWithLogs(searchEngine)//
        callResolver.collect(projectClasses)
    }
}