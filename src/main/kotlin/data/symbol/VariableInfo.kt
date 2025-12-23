package org.example.data.symbol

open class VariableInfo(
    open var name: String = "",  // имя переменной
    open var type: String = "",// её тип
) {
    fun isEnum(): Boolean {

        if (name.contains(".")) {

            val first = name.substringBefore(".")

            if (first == type)
                return true
        }

        return false
    }

    fun isField(): Boolean {

        if (name.contains(".")) {

            val first = name.substringBefore(".")

            if (first != type)
                return true
        }

        return false
    }
}