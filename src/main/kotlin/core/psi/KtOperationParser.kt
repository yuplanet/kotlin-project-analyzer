package org.example.core.psi

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*

object KtOperationParser {

    fun collectAllExpressions(fn: KtNamedFunction): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        val log = mutableListOf<String>()
        val filllog = mutableListOf<String>()

        fun process(element: PsiElement) {
            if (element is KtExpression &&
                (element is KtBinaryExpression || element is KtDotQualifiedExpression || element is KtSafeQualifiedExpression || element is KtLambdaExpression)) {
                result.add(element)
                log.add(element.text)
            }
            element.children.forEach { process(it) }
        }

        fn.bodyExpression?.let { process(it) }

        var strB = StringBuilder()
        log.forEach { strB.append(it + "\n") }

        return result
    }
}