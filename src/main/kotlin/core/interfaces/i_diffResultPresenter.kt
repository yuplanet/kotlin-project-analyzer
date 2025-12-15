package org.example.core.interfaces

import org.example.data.CallChainNode

interface i_diffResultPresenter {
    fun writeCallChainToFile(result: List<CallChainNode>)
}