package org.example.core.interfaces

import org.example.data.symbol.ObjectReference
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass

interface IProjectSearchEngine {

    fun init(projectClasses: List<KotlinClass>)


    //==== fields Properties
    fun findObjectRefByClassNameAndFieldName(className: String, fieldName: String): ObjectReference?


    // ===== Methods =====

    /**
     * Full method name format:
     * <Class>::<Receiver?>method(paramTypes):returnType
     *
     * Example:
     * ApiKeyManagementController::getApiKeyClients(String):Boolean
     */
    fun findByFullMethodName(methodName: String): ClassMethod?

    // ===== Classes =====
    fun isEnumClass(className: String): Boolean
    fun isStaticClass(className: String): Boolean

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