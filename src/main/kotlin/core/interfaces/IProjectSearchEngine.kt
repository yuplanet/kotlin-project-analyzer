package org.example.core.interfaces

import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FieldReference
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.expression.MethodValue

interface IProjectSearchEngine {

    fun init(projectClasses: List<KotlinClass>)
    fun updateMethodsHashes()

    //==== fields Properties
    fun findFieldRefByClassNameAndFieldName(className: String, fieldName: String): FieldReference?


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

    /**
     *
     * поиск по Class.Method
    */
    fun findFirstMethodByClassNameAndMethodName(className: String, methodName: String): ClassMethod?

    // ===== Classes and Meth=====
    fun findMethodByClassNameAndMethodNameAndParams(className: String, methodName: String, params: List<String>): ClassMethod?

    // ===== Classes and Meth=====
    fun findAllMethodByClassNameAndMethodNameAndParams(className: String, methodName: String, params: List<String>): List<ClassMethod>


    fun findMethodByClassNameAndMethodNameAndParamsCount(
        className: String,
        methodName: String,
        paramsCount: Int,
    ): ClassMethod?


    /**
     *
     * поиск по Class.Method(params):type
     */
    fun findMethodByClassNameAndFullMethodName(className: String, methodFullName: String): ClassMethod?

    fun findMethodByFullName(fullName: String): ClassMethod?

    fun findMethodBySignature(signature: String): ClassMethod?
    /**
     * Возвращает все ClassMethod из текущего класса, родителя и наследника.
     * поиск по Class.Method(params):type
     */
    fun findAllMethodByClassNameAndFullMethodName(className: String, methodFullName: String): List<ClassMethod>


    fun findCallerMethodsByMethod(method:ClassMethod):List<ClassMethod>
}