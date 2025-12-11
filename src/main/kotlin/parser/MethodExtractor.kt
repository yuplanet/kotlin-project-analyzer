package org.example.parser

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

data class MethodInfo(
    val className: String,
    val name: String,
    val parameters: List<String>,
    val body: String
)

object MethodExtractor {
    fun extractMethods(file: KtFile): List<MethodInfo> {
        val methods = mutableListOf<MethodInfo>()
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                val className = function.getStrictParentOfType<KtClassOrObject>()?.name ?: "TopLevel"
                val params = function.valueParameters.map { it.text }
                val body = function.bodyExpression?.text ?: ""
                methods.add(MethodInfo(className ?: "Unknown", function.name ?: "unknown", params, body))
            }
        })
        return methods
    }
}