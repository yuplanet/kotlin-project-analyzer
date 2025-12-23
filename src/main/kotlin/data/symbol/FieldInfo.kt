package org.example.data.symbol

class FieldInfo (
    var className: String = "",  // имя переменной

    override var name: String = "",// её тип
    override var type: String = "",// её тип
): VariableInfo(name, type)