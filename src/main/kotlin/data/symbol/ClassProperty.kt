package org.example.data.symbol

import org.example.data.symbol.expression.AssignmentExpression
import org.jetbrains.kotlin.psi.KtProperty

data class ClassProperty (

    var name: String,
    var property: KtProperty,
    val parentClass: KotlinClass,

    var type: String,
    var originalType: String = type.replace("?","")
) {
    var fullExpressions: MutableList<AssignmentExpression> = mutableListOf()

    //refs
    val callRecords: MutableList<ObjectReference> = mutableListOf() // target method
    val reverseCallRecords: MutableList<ObjectReference> = mutableListOf()

    fun syncData() {
        originalType = type.replace("?", "")
    }
}