package org.example.core.utils

import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.jetbrains.kotlin.psi.KtParameter

class Searcher(_allClasses: List<KotlinClass>) {

    private var allClasses = listOf<KotlinClass>()
    private var allMethods = listOf<ClassMethod>()

    //key - full name for class: path+name+args+return type
    private var classFullNameDictionary: MutableMap<Int, MutableList<KotlinClass>> = mutableMapOf()
    private var classSimpleNameDictionary: MutableMap<Int, MutableList<KotlinClass>> = mutableMapOf()

    //key - full name for method: name+args+return type
    private var methodFullNameDictionary: MutableMap<Int, MutableList<ClassMethod>> = mutableMapOf()
    private var methodSimpleNameDictionary: MutableMap<Int, MutableList<ClassMethod>> = mutableMapOf()

    init {
        allClasses = _allClasses
        allMethods = allClasses.flatMap { it.functionCalls }


        // Инициализация classDictionary
        for (cls in allClasses) {
            val key = cls.path.hashCode() // метод, который возвращает hashCode fullName
            classFullNameDictionary.computeIfAbsent(key) { mutableListOf() }.add(cls)


            val nameKey = cls.name.hashCode()
            classSimpleNameDictionary.computeIfAbsent(nameKey) { mutableListOf() }.add(cls)
        }

        // Инициализация methodDictionary
        for (method in allMethods) {
            val key = method.fullName.hashCode() // метод, который возвращает hashCode fullName
            methodFullNameDictionary.computeIfAbsent(key) { mutableListOf() }.add(method)

            val nkey = method.name.hashCode() // метод, который возвращает hashCode fullName
            methodSimpleNameDictionary.computeIfAbsent(nkey) { mutableListOf() }.add(method)
        }
    }


    fun findByClassName(className: String): KotlinClass? {
        val key = className.hashCode()
        val candidates = classSimpleNameDictionary[key] ?: emptyList()
        return candidates.firstOrNull { it.ktClassObject.name == className }
    }

    fun finFullMethodName(
        methodName: String,
    ): ClassMethod? {

        val key = methodName.hashCode()
        val candidates = methodFullNameDictionary[key] ?: emptyList()

        val res = candidates.firstOrNull { method ->
            // проверяем класс
            method.fullName == methodName
        }

        return res
    }

    fun finMethodByClassByMethodNameByParams(
        className: String,
        methodName: String,
        params: List<String>
    ): ClassMethod? {

        val pclass = findByClassName(className) ?: return null

        val key = methodName.hashCode()
        val candidates = methodSimpleNameDictionary[key] ?: emptyList()

        val res = candidates.firstOrNull { method ->
            // проверяем класс
            method.fullName.contains("${pclass.ktClassObject.name}::$methodName") &&
                    // проверяем имя метода
                    method.name == methodName &&
                    // проверяем параметры
                    areParamsEqual(params, method.parameters)
        }

        return res
    }


    /**
     * Проверяет, совпадают ли два списка типов аргументов.
     * Возвращает true, если списки одной длины и все элементы на соответствующих позициях равны.
     */
    fun areParamsEqual(targetParams: List<String>, methodParams: List<KtParameter>): Boolean {
        val types = methodParams.map { it.typeReference?.text ?: "Any" }
        if (types.size != targetParams.size) return false
        return types.indices.all { types[it] == targetParams[it] }
    }
}