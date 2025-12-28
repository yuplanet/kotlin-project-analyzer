package org.example.core.psi

import org.example.core.interfaces.ITemporaryVariableStorage

class TemporaryVariableStorage : ITemporaryVariableStorage {

    private data class VariableRecord(
        var temporaryName: String = "",
        var value: String = "",
        var previous: VariableRecord? = null
    )

    private var storage = mutableListOf<VariableRecord>()
    private var tmpCounter = 1

    // Добавление новой записи с автоматическим temp
    override fun add(value: String): Pair<String, String> {
        val tmpName = "tmp${tmpCounter++}"
        val previousRecord = storage.lastOrNull()
        storage.add(VariableRecord(tmpName, value, previousRecord))
        return tmpName to value
    }

    // Метод, который строит полное выражение для temp
    fun getFullExpression(tempName: String): String? {
        val record = storage.asReversed().firstOrNull { it.temporaryName == tempName } ?: return null
        return buildFullExpr(record)
    }

    private fun buildFullExpr(record: VariableRecord): String {
        return record.previous?.let { buildFullExpr(it) + "." + record.temporaryName } ?: record.temporaryName
    }

    // Остальные методы интерфейса
    override fun add(name: String, value: String): Pair<String, String>? {
        storage.firstOrNull { it.value == value }?.let { return null }
        val tmpName = "tmp${tmpCounter++}"
        val previousRecord = storage.lastOrNull()
        storage.add(VariableRecord(tmpName, value, previousRecord))
        return tmpName to value
    }

    override fun getByKey(key: String): Pair<String, String>? {
        return storage.asReversed().firstOrNull { it.temporaryName == key }?.let { it.temporaryName to it.value }
    }

    override fun getValueByKey(key: String): String? {
        return storage.asReversed().firstOrNull { it.temporaryName == key }?.value
    }

    override fun getLast(): Pair<String, String>? {
        return storage.lastOrNull()?.let { it.temporaryName to it.value }
    }

    override fun getLastByKey(key: String): Pair<String, String>? {
        return storage.asReversed().firstOrNull { it.temporaryName == key }?.let { it.temporaryName to it.value }
    }

    override fun getLastByValue(value: String): Pair<String, String>? {
        val record = storage.asReversed().firstOrNull { it.value == value }
        if (record != null) return record.temporaryName to record.value
        return null
    }
}
