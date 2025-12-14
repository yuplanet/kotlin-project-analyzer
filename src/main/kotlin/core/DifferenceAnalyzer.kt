package org.example.core

import org.example.core.interfaces.i_projectDifferenceAnalyzer
import org.example.data.DiffResult
import org.example.data.KotlinClass
import org.example.data.KotlinMethod

class DifferenceAnalyzer : i_projectDifferenceAnalyzer {
        private var developClasses: List<KotlinClass> = listOf()
        private var featureClasses: List<KotlinClass> = listOf()
        /**
         * Сравнивает develop и feature и возвращает DiffResult
         */
    override fun analyzeProjectDifferences(
        mainProject: List<KotlinClass>,
        branchProject: List<KotlinClass>
    ): DiffResult {
        developClasses = mainProject
        featureClasses = branchProject

        // 1. Строим карты методов по fullName
        val developMethodsByFullName: Map<String, KotlinMethod> =
            developClasses.flatMap { it.functionCalls }.associateBy { it.fullName }

        val featureMethodsByFullName: Map<String, KotlinMethod> =
            featureClasses.flatMap { it.functionCalls }.associateBy { it.fullName }

        // 2. Найдём добавленные методы
        val addedMethods = featureMethodsByFullName.keys
            .filter { it !in developMethodsByFullName.keys }
            .map { featureMethodsByFullName[it]!! }

        // 3. Найдём удалённые методы
        val removedMethods = developMethodsByFullName.keys
            .filter { it !in featureMethodsByFullName.keys }
            .map { developMethodsByFullName[it]!! }

        // 4. Найдём изменённые методы
        val changedMethods = featureMethodsByFullName.keys
            .filter { it in developMethodsByFullName.keys }
            .mapNotNull { fullName ->
                val devMethod = developMethodsByFullName[fullName]!!
                val featMethod = featureMethodsByFullName[fullName]!!

                val devBody = devMethod.function.bodyExpression?.text
                val featBody = featMethod.function.bodyExpression?.text

                if (devBody != featBody) featMethod else null
            }

        return DiffResult(
            added = addedMethods,
            removed = removedMethods,
            changed = changedMethods
        )
    }
}