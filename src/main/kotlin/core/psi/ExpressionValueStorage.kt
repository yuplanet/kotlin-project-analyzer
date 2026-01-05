package org.example.core.psi

import org.example.core.interfaces.ITemporaryVariableStorage
import org.example.data.symbol.expression.ExpressionValue
import org.example.data.symbol.expression.VariableValue

class ExpressionValueStorage : ITemporaryVariableStorage {

    private data class VariableRecord(
        var recordName: String = "",
        var rawValue: String = "",
        var value: ExpressionValue
    )

    private var storage = mutableListOf<VariableRecord>()
    private var tmpCounter = 1

    override fun add(expr: ExpressionValue): String {
        val tmpName = "tmp${tmpCounter++}"

        val record = VariableRecord(
            recordName =  tmpName,
            rawValue = expr.rawValue,
            value = expr
        )
        storage.add(record)

        return tmpName
    }

    override fun getByRawValue(rawValue: String): ExpressionValue? {
        val record = storage.asReversed().firstOrNull { it.rawValue == rawValue }
        return record?.value
    }

    override fun getByVariableBody(expr: ExpressionValue): String? {
        TODO("Not yet implemented")
    }

    override fun getVariableByName(variableName: String): VariableValue? {

        val record = storage.firstOrNull { it is VariableValue && it.variableName == variableName }
        return record?.value as VariableValue
    }
}
