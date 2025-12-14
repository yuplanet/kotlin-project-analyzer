package org.example.core.interfaces

import org.example.data.DiffResult
import org.example.data.KotlinClass

interface i_diffResultPresenter {
    fun writeCallChainToFile(result: DiffResult, allClasses: List<KotlinClass>)
}