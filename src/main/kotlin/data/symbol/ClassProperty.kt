package org.example.data.symbol

import org.example.data.symbol.expression.AssignmentExpression
import org.jetbrains.kotlin.psi.KtProperty

data class ClassProperty (

    var name: String,
    var type: String,
    var property: KtProperty,

    var expression: MutableList<AssignmentExpression> = mutableListOf(),

    var callRecords: MutableList<ObjectReference> = mutableListOf(),
    var reverseCallRecords: MutableList<ObjectReference> = mutableListOf(),
)
