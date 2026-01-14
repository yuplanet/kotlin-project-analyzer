package org.example.core

import org.example.core.interfaces.IProjectDifferenceAnalyzer
import org.example.data.analyzer.ProjectDiffResult
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass

class DifferenceAnalyzer : IProjectDifferenceAnalyzer {
        private var developClasses: List<KotlinClass> = listOf()
        private var featureClasses: List<KotlinClass> = listOf()
    private var result = ProjectDiffResult()
        /**
         * Сравнивает develop и feature и возвращает DiffResult
         */
    override fun analyzeProjectDifferences(
        mainProject: List<KotlinClass>,
        branchProject: List<KotlinClass>
    ): ProjectDiffResult {

            developClasses = mainProject
            featureClasses = branchProject

            analyzeMethods()

            return result
        }

    fun analyzeMethods(){
        // 1. Строим карты методов по fullName
        val developMethodsByFullName: Map<String, ClassMethod> =
            developClasses.flatMap { it.functionCalls }.associateBy { it.fullName }

        val featureMethodsByFullName: Map<String, ClassMethod> =
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

        result.addedMethods = addedMethods
        result.changedMethods = changedMethods
        result.removedMethods = removedMethods
    }

    private fun analyzeParameters(developClasses: ClassMethod, featureClasses: ClassMethod) {
        val devParamsByName = developClasses.parameters.associateBy { it.name }
        val featParamsByName = featureClasses.parameters.associateBy { it.name }

        // Добавленные параметры
        val added = featParamsByName.keys.filter { it !in devParamsByName.keys }
            .map { featParamsByName[it]!! }

        // Удалённые параметры
        val removed = devParamsByName.keys.filter { it !in featParamsByName.keys }
            .map { devParamsByName[it]!! }

        // Изменённые параметры (тип изменился)
        val changed = featParamsByName.keys
            .filter { it in devParamsByName.keys && devParamsByName[it]!!.type != featParamsByName[it]!!.type }
            .map { featParamsByName[it]!! }

        result.addedParameters += added
        result.removedParameters += removed
        result.changedParameters += changed
    }

    private fun analyzeProperties(devClass: KotlinClass, featClass: KotlinClass) {
        // Сопоставляем свойства по имени
        val devPropsByName = devClass.propertyReferences.associateBy { it.name }
        val featPropsByName = featClass.propertyReferences.associateBy { it.name }

        // Добавленные
        val added = featPropsByName.keys
            .filter { it !in devPropsByName.keys }
            .map { featPropsByName[it]!! }

        // Удалённые
        val removed = devPropsByName.keys
            .filter { it !in featPropsByName.keys }
            .map { devPropsByName[it]!! }

        // Изменённые — сравниваем тип
        val changed = featPropsByName.keys
            .filter { it in devPropsByName.keys }
            .filter { name ->
                val devType = devPropsByName[name]?.type ?: "_"
                val featType = featPropsByName[name]?.type ?: "_"
                devType != featType
            }
            .map { featPropsByName[it]!! }

        // Добавляем в результат
        result.addedProperties += added
        result.removedProperties += removed
        result.changedProperties += changed
    }
}