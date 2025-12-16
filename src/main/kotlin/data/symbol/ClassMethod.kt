package org.example.data.symbol

import org.example.data.reference.MethodCallReference
import org.jetbrains.kotlin.psi.KtNamedFunction

data class ClassMethod(

    /**
     * <путь_к_файлу>.<имя_класса>::<имя_метода> ( параметры )
     */
    val fullName: String,
    val function: KtNamedFunction,

    val callRecords: MutableList<MethodCallReference> = mutableListOf(),
    val reverseCallRecords: MutableList<MethodCallReference> = mutableListOf()
)