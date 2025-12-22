package org.example.data.symbol

import org.example.data.symbol.expression.BaseExpression
import org.jetbrains.kotlin.psi.KtProperty

data class ClassProperty (

    var name: String,
    var type: String,
    var property: KtProperty,

    var expression: MutableList<BaseExpression> = mutableListOf(),

    var callRecords: MutableList<ObjectReference> = mutableListOf(),
    var reverseCallRecords: MutableList<ObjectReference> = mutableListOf(),
)
