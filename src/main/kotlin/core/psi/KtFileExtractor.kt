package org.example.core.psi

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
* Класс по распаковен файлов txt на KtFile, KtClass, KtFunc
*
*/
object KtFileExtractor {
    fun createProject(): Project {
        val disposable: Disposable = Disposer.newDisposable()

        val configuration = CompilerConfiguration().apply {
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.Companion.NONE)
            put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_21)
        }

        val environment = KotlinCoreEnvironment.Companion.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        return environment.project
    }

    fun createPsiFile(project: Project, fileName: String, code: String): KtFile {
        // Используем KtPsiFactory, чтобы создать KtFile в памяти
        val psiFactory = KtPsiFactory(project, false)
        return psiFactory.createFile(fileName, code)
    }

    fun getClassMethods(cls: KtClassOrObject): List<KtNamedFunction> {
        // Берём функции, которые объявлены в теле класса
        val classFunctions = cls.declarations.filterIsInstance<KtNamedFunction>().toMutableList()

        // Добавляем функции из companion object
        cls.companionObjects.forEach { companion ->
            classFunctions.addAll(companion.declarations.filterIsInstance<KtNamedFunction>())
        }

        return classFunctions
    }
}