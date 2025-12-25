package org.example.core.interfaces

interface ITemporaryVariableStorage {

    fun add(value: String): Pair<String, String>
    fun add(name: String, value: String): Pair<String, String>?

    fun getByKey(key: String): Pair<String, String>?
    fun getValueByKey(key: String): String?

    fun getLast(): Pair<String, String>?
    fun getLastByKey(key: String): Pair<String, String>?

    fun getLastByValue(value: String): Pair<String, String>?
}
