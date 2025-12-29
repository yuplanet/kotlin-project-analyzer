package org.example.data.symbol.expression

class FieldInfo (

    /**
     *  Имя экземпляра
     */
    var className: String = "unknown",  // имя переменной

    /**
     * тип класса (если Object то совпадает с именем )
     */
    var classType: String = "unknown",  // имя переменной

    /**
     * имя поля Class.Field <- name
     */
    override var name: String = "unknown",// её тип


    /**
     * тип поля
     */
    override var type: String = "",// её тип
): VariableInfo(name, type)