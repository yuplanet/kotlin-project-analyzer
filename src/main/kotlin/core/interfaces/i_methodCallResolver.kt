package org.example.core.interfaces

import org.example.data.KotlinClass

interface i_methodCallResolver {

    fun resolve(allClasses: List<KotlinClass>)
}