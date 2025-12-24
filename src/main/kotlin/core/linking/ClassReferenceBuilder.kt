package org.example.core.linking

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.psi.KtExpressionChainBuilder
import org.example.data.symbol.MethodReference
import org.example.data.symbol.ObjectReference
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.expression.FieldInfo
import org.example.data.symbol.FieldReference
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.expression.MethodInfo
import org.example.data.symbol.expression.VariableInfo
import org.example.data.symbol.expression.FieldFromVariableExpression
import org.example.data.symbol.expression.FieldFromFieldExpression
import org.example.data.symbol.expression.VariableFromMethodExpression
import org.example.data.symbol.expression.VariableFromFieldExpression
import java.io.File

class ClassReferenceBuilder (): IClassReferenceBuilder {

    private lateinit var searchEngine: IProjectSearchEngine

    override fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine) {

        this.searchEngine = searchEngine


        // 11 collect function expressions
        collectExpressions2(projectClasses)

        //Methods
        buildFunctionCallRecords(projectClasses) // to do


        buildReverseFunctionCallRecords(projectClasses)
    }

    fun collectExpressions(projectClasses: List<KotlinClass>)  {
        val logDirectory = File("logs")
        if (!logDirectory.exists()) logDirectory.mkdirs()  // создаём папку logs

        for (cls in projectClasses) {
            val logFile = File(logDirectory, "${cls.name}_expressions.log")

            logFile.printWriter().use { out ->
                out.println("Class: ${cls.name}")
                out.println("Path: ${cls.path}")
                out.println("Methods expressions:")

                for (method in cls.functionCalls) {
                    out.println("\nMethod: ${method.name}")

                    // Собираем expressions для метода
                    val expressionCollector = KtExpressionChainBuilder()
                    expressionCollector.collectExpressions(method, cls, searchEngine)

                    for (expr in method.fullExpressions) {
                        when (expr) {
                            is VariableFromMethodExpression -> {
                                out.println("VariableAssignment -> target: ${expr.target?.name}, receiver: ${expr.receiver?.name}, method: ${expr.method?.name}")
                            }
                            is FieldFromVariableExpression -> {
                                out.println("FieldAssignment -> target: ${expr.target?.name}, source: ${expr.source?.name}")
                            }
                            is VariableFromFieldExpression -> {
                                out.println("VariableToField -> target: ${expr.target?.name}, source: ${expr.source?.name}")
                            }
                            is FieldFromFieldExpression -> {
                                out.println("FieldToField -> target: ${expr.target?.name}, source: ${expr.source?.name}")
                            }
                            else -> {
                                out.println("Unknown expression: $expr")
                            }
                        }
                    }
                }
            }
        }
    }

    fun collectExpressions2(projectClasses: List<KotlinClass>) {

        for (cls in projectClasses) {

            for (method in cls.functionCalls) {
                //if (method.name != "sendScheduledEnvelopeNotification")
                //    continue

                val expressionCollector = KtExpressionChainBuilder();
                expressionCollector.collectExpressions(method, cls, searchEngine)

                println(1)
            }
        }
    }

    private fun getRefToMethod(callingClass: String, callingMethod: MethodInfo): MethodReference? {

        val parentCLass = searchEngine.findByClassName(callingClass)


        return if (parentCLass != null) {
            MethodReference(
                parentClass = parentCLass,
                name = callingMethod.name,
                method = callingMethod,
            )
        } else null
    }

    private fun getRefToField(callingClass: String, callingField: VariableInfo): FieldReference? {

        val parentCLass = searchEngine.findByClassName(callingClass)

        return if (parentCLass != null) {
            val field = FieldInfo(
                name =  callingField.name,
                type =  callingField.type,
                className = callingClass
            )

            FieldReference(
                parentClass = parentCLass,
                name = callingField.name,
                field = field,
            )
        } else null
    }

    private fun addFieldRef(className: String, fieldName: String): ObjectReference? {

        val callingClass = searchEngine.findByClassName(className)
        val member = if (callingClass != null) {
            searchEngine.findObjectRefByClassNameAndFieldName(className, fieldName)
        } else
            null

        return member
    }

    private fun addRefToField(className: String, fieldName: String, classMethod: ClassMethod) {
        val target = addFieldRef(className, fieldName)
        if (target != null)
            classMethod.callRecords.add((target))
    }

    fun buildFunctionCallRecords(projectClasses: List<KotlinClass>) {

        for (cls in projectClasses) {
            for (method in cls.functionCalls)

            {
                for (exprBase in method.fullExpressions) {

                    when (exprBase) {

                        is VariableFromMethodExpression -> buildVariableAssignmentExpressionReference(cls,method, exprBase as VariableFromMethodExpression)

                        is VariableFromFieldExpression -> buildVariableToFieldAssignmentExpression(cls,method, exprBase as VariableFromFieldExpression)

                        is FieldFromVariableExpression -> buildFieldAssignmentExpression(cls,method, exprBase as FieldFromVariableExpression)

                        is FieldFromFieldExpression ->buildFieldToFieldAssignmentExpression(cls,method, exprBase as FieldFromFieldExpression)
                    }
                }
            }
        }
    }

    fun buildFieldToFieldAssignmentExpression(currentClass: KotlinClass, method: ClassMethod, expr: FieldFromFieldExpression){
        //calling method!!!
        if (expr.target != null) {
            var ref = getRefToField(currentClass.name, expr.target!!)
            ref?.let { method.callRecords.add(it) }

        }
        //calling method!!!
        if (expr.source != null) {
            val ref = getRefToField(currentClass.name, expr.source)
            ref?.let { method.callRecords.add(it) }
        }
    }

    fun buildFieldAssignmentExpression(currentClass: KotlinClass, method: ClassMethod, expr: FieldFromVariableExpression){
        //calling method!!!
        if (expr.target != null) {
            var ref = getRefToField(currentClass.name, expr.target!!)
            ref?.let { method.callRecords.add(it) }

        }
        //calling method!!!
        if (expr.source != null) {
            val ref = getRefToField(currentClass.name, expr.source)
            ref?.let { method.callRecords.add(it) }
        }
    }

    fun buildVariableAssignmentExpressionReference(currentClass: KotlinClass, method: ClassMethod, expr: VariableFromMethodExpression) {

        //calling method!!!
        if (expr.receiver != null) {
            val ref = getRefToMethod(expr.receiver.type, expr.method)
            ref?.let { method.callRecords.add(it) }
        } else if (expr.receiver == null || expr.receiver.type == "this") {
            val ref = getRefToMethod(currentClass.name, expr.method)
            ref?.let { method.callRecords.add(it) }
        }

        for (param in expr.method.parameters) {
            val ref = getRefToField(currentClass.name, param)
            ref?.let { method.callRecords.add(it) }
        }
        //calling field
    }

    fun buildVariableToFieldAssignmentExpression(currentClass: KotlinClass, method: ClassMethod, expr:VariableFromFieldExpression) {
        //calling method!!!
        if (expr.target != null) {
            val ref = getRefToField(currentClass.name, expr.target)
            ref?.let { method.callRecords.add(it) }

        }
        //calling method!!!
        if (expr.source != null) {
            val ref = getRefToField(currentClass.name, expr.source)
            ref?.let { method.callRecords.add(it) }
        }
    }

    fun buildReverseFunctionCallRecords(projectClasses: List<KotlinClass>){

        for (cls in projectClasses) {

            for (method in cls.functionCalls) {

                for (exprBase in method.fullExpressions) {

                    var expr = exprBase as VariableFromMethodExpression

                    //fields
                    addRefToField(expr.target!!.type, expr.target!!.name, method)
                    addRefToField(expr.receiver!!.type, expr.receiver.name, method)

                    for (param in expr.method.parameters) {
                        addRefToField(param.type, param.name, method)
                    }


                    //method
                    val targetMethod = expr.method.name
                    val targetClass = expr.receiver.type
                    val parameters = expr.method.parameters.map { it.type }
                    val callingClass = searchEngine.findByClassName(targetClass)

                    if (callingClass != null) {
                        val classMethod = searchEngine.findMethodByClassNameAndMethodNameAndParams(
                            targetClass,
                            targetMethod,
                            parameters
                        )

                        if (classMethod != null) {
                            val callRef = MethodReference(
                                name = targetMethod,
                                method = expr.method,
                                parentClass = callingClass
                            )
                            // Добавляем в callRecords вызывающего метода
                            method.callRecords.add(callRef)

                            val reverseCallRef = MethodReference(
                                name = method.name,
                                method = expr.method,
                                parentClass = cls
                            )
                            classMethod.reverseCallRecords.add(reverseCallRef)
                        }
                    }
                }
            }
        }
    }
}