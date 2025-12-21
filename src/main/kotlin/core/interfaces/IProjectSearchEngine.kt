package org.example.core.interfaces

import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.jetbrains.kotlin.psi.KtFile

interface IProjectSearchEngine {

    fun init(projectClasses: List<KotlinClass>)



    // ===== KtFiles =====

    fun initKtFilesLibrary(ktFiles: Map<KtFile, List<KotlinClass>>)

    fun getKtFileByClassName(className: String): KtFile?

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