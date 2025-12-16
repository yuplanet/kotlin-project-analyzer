package org.example.data.analyzer

import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.example.data.symbol.ClassProperty

data class ProjectDiffResult(
    var addedMethods: List<ClassMethod> = listOf(),
    var removedMethods: List<ClassMethod> = listOf(),
    var changedMethods: List<ClassMethod> = listOf(),

    var addedParameters: List<ClassParameter> = listOf(),
    var removedParameters: List<ClassParameter> = listOf(),
    var changedParameters: List<ClassParameter> = listOf(),

    var addedProperties: List<ClassProperty> = listOf(),
    var removedProperties: List<ClassProperty> = listOf(),
    var changedProperties: List<ClassProperty> = listOf()
)