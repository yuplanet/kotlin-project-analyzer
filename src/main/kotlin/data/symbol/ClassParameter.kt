package org.example.data.symbol

import org.example.data.symbol.expression.AssignmentExpression
import org.jetbrains.kotlin.psi.KtParameter

data class ClassParameter (

    var name: String,
    var type: String,
    var property: KtParameter,
    val parentClass: KotlinClass,
    var fullExpressions: MutableList<AssignmentExpression> = mutableListOf(),

    var callRecords: MutableList<ObjectReference> = mutableListOf(),
    var reverseCallRecords: MutableList<ObjectReference> = mutableListOf(),
){
    var originalType: String

    init {
        originalType = type.replace("?","")
    }
}