package org.example.core.linking

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.psi.KtExpressionChainBuilder
import org.example.data.symbol.KotlinClass

class ClassReferenceBuilder (): IClassReferenceBuilder {

    private lateinit var searchEngine: IProjectSearchEngine

    override fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine) {

        this.searchEngine = searchEngine


        // 11 collect function expressions
        collectExpressions(projectClasses)
    }

    fun collectExpressions(projectClasses: List<KotlinClass>) {

        for (cls in projectClasses) {

            for (method in cls.functionCalls) {
                 if (method.name != "sendScheduledEnvelopeNotification")
                     continue

                //val expressionCollectorw = NewKtExpressionChainBuilder(method, cls, searchEngine);
                //expressionCollectorw.collectTopLevelExpressions(method.function)

                val expressionCollector = KtExpressionChainBuilder(method, cls, searchEngine);
                expressionCollector.collectTopLevelExpressions()

                println(1)
            }
        }
    }
}