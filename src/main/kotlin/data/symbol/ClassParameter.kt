package org.example.data.symbol

import org.example.data.symbol.expression.AssignmentExpression
import org.jetbrains.kotlin.psi.KtParameter

data class ClassParameter (

    var name: String,
    var property: KtParameter,
    val parentClass: KotlinClass,

    var type: String,
    var originalType: String = type.replace("?",""),
) {

    var fullExpressions: MutableList<AssignmentExpression> = mutableListOf()

    var callRecords: MutableList<ObjectReference> = mutableListOf()
    var reverseCallRecords: MutableList<ObjectReference> = mutableListOf()

    fun syncData() {
        originalType = type.replace("?", "")
    }
}