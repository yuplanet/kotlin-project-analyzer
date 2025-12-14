package org.example.core.interfaces

import org.example.data.DiffResult

interface i_diffResultPresenter {
    fun writeCallChainToFile(result: DiffResult)
}