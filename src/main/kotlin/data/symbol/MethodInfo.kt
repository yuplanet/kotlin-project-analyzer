package org.example.data.symbol

import org.eclipse.jgit.diff.RawText

data class MethodInfo (
    var name: String ="",
    var returnType: String = "",
    var parameters: MutableList<VariableInfo> = mutableListOf(),
    var innerInvoke: Boolean = false,
    var rawContent: String = "",
)