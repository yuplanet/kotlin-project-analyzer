package org.example.data

import org.jetbrains.kotlin.psi.KtNamedFunction

data class KotlinMethod(

    /**
     * <путь_к_файлу>.<имя_класса>::<имя_метода> ( параметры )
     */
    val fullName: String,
    val function: KtNamedFunction,

    val callRecords: MutableList<CallMethod> = mutableListOf()
)