package org.example.data.symbol.expression

open class VariableInfo(

    open var name: String = "unknown",  // имя переменной
    open var type: String = "unknown",// её тип

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