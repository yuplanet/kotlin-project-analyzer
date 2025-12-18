package org.example.core.interfaces

import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass

interface IProjectSearchEngine {

    fun init(projectClasses: List<KotlinClass>)

    // ===== Methods =====


    fun findByFullMethodName(methodName: String): ClassMethod?

    // ===== Classes =====

    fun findByClassName(className: String): KotlinClass?

    fun findByClassNameAndMethodName(className: String, methodName: String): List<ClassMethod>

    // ===== Classes and Meth=====
    fun findFirstMethodByClassNameAndMethodName(
        className: String,
        methodName: String,
    ): ClassMethod?

    // ===== Classes and Meth=====
    fun findMethodByClassNameAndMethodNameAndParams(
        className: String,
        methodName: String,
        params: List<String>,
    ): ClassMethod?
}