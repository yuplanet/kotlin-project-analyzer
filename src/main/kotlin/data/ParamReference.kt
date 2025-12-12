package org.example.data

import org.jetbrains.kotlin.psi.KtProperty

data class ParamReference (
    var ParamId: Int,
    var KtProperty: KtProperty,

    var CallRecord: CallMethod
)
