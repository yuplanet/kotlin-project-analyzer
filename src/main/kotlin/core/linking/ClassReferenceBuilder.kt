package org.example.core.linking

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.psi.KtFunctionExpressionCollector
import org.example.data.symbol.MethodReference
import org.example.data.symbol.ObjectReference
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.FieldInfo
import org.example.data.symbol.FieldReference
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.MethodInfo
import org.example.data.symbol.VariableInfo
import org.example.data.symbol.expression.FieldAssignmentExpression
import org.example.data.symbol.expression.FieldToFieldAssignmentExpression
import org.example.data.symbol.expression.VariableAssignmentExpression
import org.example.data.symbol.expression.VariableToFieldAssignmentExpression
import java.io.File

class ClassReferenceBuilder (): IClassReferenceBuilder {

    private lateinit var searchEngine: IProjectSearchEngine

    override fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine) {

        this.searchEngine = searchEngine


        // 11 collect function expressions
        collectExpressions(projectClasses)

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
                    val expressionCollector = KtFunctionExpressionCollector()
                    expressionCollector.collectExpressions(method, cls, searchEngine)

                    for (expr in method.fullExpressions) {
                        when (expr) {
                            is VariableAssignmentExpression -> {
                                out.println("VariableAssignment -> target: ${expr.target?.name}, receiver: ${expr.receiver?.name}, method: ${expr.method?.name}")
                            }
                            is FieldAssignmentExpression -> {
                                out.println("FieldAssignment -> target: ${expr.target?.name}, source: ${expr.source?.name}")
                            }
                            is VariableToFieldAssignmentExpression -> {
                                out.println("VariableToField -> target: ${expr.target?.name}, source: ${expr.source?.name}")
                            }
                            is FieldToFieldAssignmentExpression -> {
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
                if (method.name != "sendScheduledEnvelopeNotification")
                    continue

                val expressionCollector = KtFunctionExpressionCollector();
                expressionCollector.collectExpressions(method, cls, searchEngine)
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

                        is VariableAssignmentExpression -> buildVariableAssignmentExpressionReference(cls,method, exprBase as VariableAssignmentExpression)

                        is VariableToFieldAssignmentExpression -> buildVariableToFieldAssignmentExpression(cls,method, exprBase as VariableToFieldAssignmentExpression)

                        is FieldAssignmentExpression -> buildFieldAssignmentExpression(cls,method, exprBase as FieldAssignmentExpression)

                        is FieldToFieldAssignmentExpression ->buildFieldToFieldAssignmentExpression(cls,method, exprBase as FieldToFieldAssignmentExpression)
                    }
                }
            }
        }
    }

    fun buildFieldToFieldAssignmentExpression(currentClass: KotlinClass, method: ClassMethod, expr: FieldToFieldAssignmentExpression){
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

    fun buildFieldAssignmentExpression(currentClass: KotlinClass, method: ClassMethod, expr: FieldAssignmentExpression){
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

    fun buildVariableAssignmentExpressionReference(currentClass: KotlinClass, method: ClassMethod, expr: VariableAssignmentExpression) {

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

    fun buildVariableToFieldAssignmentExpression(currentClass: KotlinClass, method: ClassMethod, expr:VariableToFieldAssignmentExpression) {
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

                    var expr = exprBase as VariableAssignmentExpression

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