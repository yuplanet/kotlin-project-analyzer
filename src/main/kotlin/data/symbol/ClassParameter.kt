package org.example.data.symbol

import org.example.data.symbol.expression.BaseExpression
import org.jetbrains.kotlin.psi.KtParameter

data class ClassParameter (

    var name: String,
    var type: String,
    var property: KtParameter,

    var expression: MutableList<BaseExpression> = mutableListOf(),

    var callRecords: MutableList<ObjectReference> = mutableListOf(),
    var reverseCallRecords: MutableList<ObjectReference> = mutableListOf(),
)