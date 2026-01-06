package org.example.data.symbol.expression

class FieldValue (

    /**
     * имя поля Class.Field <- name
     */
    var fieldName: String = "unknown",// её тип

    /**
     * тип поля Class.Field <- name type
     */
    var fieldType: String = "unknown",// её тип

    /**
     * имя класса name -> Class.Field
     */
    var qualifier: String = "unknown",

    /**
     * тип класса name type -> Class.Field
     */
    var qualifierType: String = "unknown",

): ExpressionValue() {
    override var rawValue: String = "unknown"
        get() = "$qualifier.$fieldName"

    override var valueType: String = "unknown"
        get() = fieldType

    var fieldSignature: String = "unknown"
        get() = "$qualifierType.$fieldType"
}

//Class.Field.


// Class - var
// Field - var