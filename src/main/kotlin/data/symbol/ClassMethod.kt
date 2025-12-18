package org.example.data.symbol

import org.example.data.reference.MethodCallReference
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

data class ClassMethod(


    val name: String,
    /**
     * <путь_к_файлу>.<имя_класса>::<имя_метода> ( параметры )
     *ApiKeyManagementController::getApiKeyClients():Boolean
     */
    val fullName: String,

    val parameters: List<KtParameter>,
    val parameterTypes: List<String>,

    val function: KtNamedFunction,

    val callRecords: MutableList<MethodCallReference> = mutableListOf(),
    val reverseCallRecords: MutableList<MethodCallReference> = mutableListOf()
)