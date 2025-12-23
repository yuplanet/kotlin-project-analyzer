package org.example.core.psi

class TemporaryVariableStorage {

    private data class VariableRecord(

        var temporaryName: String = "",
        var value: String = "",
    )

    private var storage = mutableListOf<VariableRecord>()

    var tmpCounter = 1


    fun add(value: String): Pair<String, String> {
        val tmpName = "tmp${tmpCounter++}"
        storage.add(VariableRecord(tmpName, value))
        return tmpName to value
    }

    // Получить значение по ключу
    fun getByKey(key: String): String? {
        return storage.asReversed().firstOrNull { it.temporaryName == key }?.value
    }

    /** Получить последнюю запись по значению */
    fun getLastByValue(value: String): Pair<String, String>? {
        val record = storage.asReversed().firstOrNull { it.value == value }

        if (record != null) return record.temporaryName to record.value

        return null
    }
}