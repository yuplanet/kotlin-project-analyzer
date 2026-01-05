package org.example.core.interfaces

import org.example.data.symbol.expression.ExpressionValue
import org.example.data.symbol.expression.VariableValue

interface ITemporaryVariableStorage {

    fun add(expr: ExpressionValue): String

    fun getByRawValue(rawValue: String): ExpressionValue?

    fun getByVariableBody(expr: ExpressionValue) : String?

    fun getVariableByName(variableName: String): VariableValue?
}
