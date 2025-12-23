package org.example.core.psi

class TemporaryVariableStorage {

    private data class VariableRecord(
        var temporaryName: String = "",
        var value: String = "",
    )

    private var storage = mutableListOf<VariableRecord>()
    private var tmpCounter = 1


    fun add(value: String): Pair<String, String> {
        val tmpName = "tmp${tmpCounter++}"
        storage.add(VariableRecord(tmpName, value))
        return tmpName to value
    }

    fun add(name: String, value: String): Pair<String, String>? {
        // Проверяем, есть ли уже запись с таким значением
        storage.firstOrNull { it.value == value }?.let { return null }

        val tmpName = "tmp${tmpCounter++}"
        storage.add(VariableRecord(tmpName, value))
        return tmpName to value
    }

    // Получить значение по ключу
    fun getValueByKey(key: String): String? {
        return storage.asReversed().firstOrNull { it.temporaryName == key }?.value
    }

    fun getByKey(key: String): Pair<String, String>? {
        return storage.asReversed().firstOrNull { it.temporaryName == key }?.let { it.temporaryName to it.value }
    }


    /** Получить последнюю добавленную запись */
    fun getLast(): Pair<String, String>? {
        return storage.lastOrNull()?.let { it.temporaryName to it.value }
    }

    /** Получить последнюю запись по значению */
    fun getLastByValue(value: String): Pair<String, String>? {
        val record = storage.asReversed().firstOrNull { it.value == value }
        if (record != null) return record.temporaryName to record.value
        return null
    }

    /** Получить последнюю запись по ключу */
    fun getLastByKey(key: String): Pair<String, String>? {
        return storage.asReversed().firstOrNull { it.temporaryName == key }?.let { it.temporaryName to it.value }
    }
}