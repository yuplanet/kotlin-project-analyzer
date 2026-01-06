package org.example.data.symbol.expression

class FieldValue (

    /**
     * имя поля Class.Field <- name
     */
    var fieldName: String = "",// её тип

    /**
     * тип поля Class.Field <- name type
     */
    var fieldType: String = "",// её тип

    /**
     * имя класса name -> Class.Field
     */
    var qualifier: String = "",

    /**
     * тип класса name type -> Class.Field
     */
    var qualifierType: String = "",

): ExpressionValue() {
    override var rawValue: String = ""
        get() = "$qualifier.$fieldName"

    override var valueType: String = ""
        get() = fieldType

    var fieldSignature: String = ""
        get() = "$qualifierType.$fieldType"
}

//Class.Field.


// Class - var
// Field - var