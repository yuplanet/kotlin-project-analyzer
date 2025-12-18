package org.example.core.search

import org.example.core.utils.KtFileMapper
import org.jetbrains.kotlin.psi.KtFile

object KiFileIndexed {

    // Нужно mutableMapOf(), чтобы можно было писать library[key] = value
    private val library: MutableMap<String, KtFile> = mutableMapOf()

    fun indexFiles(ktFiles: List<KtFile>) {
        ktFiles.forEach { ktFile ->
            val classesInFile = KtFileMapper.mapKtFileToClasses(ktFile)
            classesInFile.forEach { kClass ->
                library[kClass.name] = ktFile // теперь корректно
            }
        }
    }

    fun getFileByClassName(className: String): KtFile? = library[className]
    fun hasClass(className: String): Boolean = library.containsKey(className)
}
