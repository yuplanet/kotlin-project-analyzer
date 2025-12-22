package org.example.core.interfaces

import org.example.data.reference.ObjectReference
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.example.data.symbol.KotlinClass
import org.jetbrains.kotlinx.jspo.compiler.fir.services.ClassProperty

interface IProjectSearchEngine {

    fun init(projectClasses: List<KotlinClass>)



    //==== fields Properties

    fun findObjectRefByClassNameAndFieldName(className: String, fieldName: String): ObjectReference?

    fun findPropertyRefByClassNameAndFieldName(className: String, fieldName: String): ClassProperty?

    fun findParameterRefByClassNameAndFieldName(className: String, fieldName: String): ClassParameter?


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