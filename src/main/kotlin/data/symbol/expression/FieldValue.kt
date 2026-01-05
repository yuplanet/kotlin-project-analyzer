package org.example.data.symbol.expression

class FieldValue (

    /**
     * имя поля Class.Field <- name
     */
    var fieldName: String = "unknown",// её тип

    /**
     * тип поля
     */
    var fieldType: String = "unknown",// её тип


    var qualifier: String = "unknown",

    var qualifierType: String = "unknown",


): ExpressionValue()

//Class.Field.


// Class - var
// Field - var