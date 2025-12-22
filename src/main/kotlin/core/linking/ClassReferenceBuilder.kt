package org.example.core.linking

import org.codehaus.groovy.ast.expr.VariableExpression
import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.psi.KtFunctionExpressionCollector
import org.example.data.symbol.MethodReference
import org.example.data.symbol.ObjectReference
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.KotlinClass
import org.example.data.symbol.expression.VariableAssignmentExpression

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

    fun collectExpressions(projectClasses: List<KotlinClass>) {

        for (cls in projectClasses) {

            for (method in cls.functionCalls) {
                if (method.name != "sendScheduledEnvelopeNotification")
                    continue

                val expressionCollector = KtFunctionExpressionCollector();
                expressionCollector.collectExpressions(method, cls, searchEngine)
            }
        }
    }


    private fun isStaticOrEnum(className: String): Boolean {
        val isTargetEnum = searchEngine.isEnumClass(className)
        val isTargetStatic = searchEngine.isStaticClass(className)

        return isTargetEnum || isTargetStatic
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
                        }
                    }
                }
            }
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