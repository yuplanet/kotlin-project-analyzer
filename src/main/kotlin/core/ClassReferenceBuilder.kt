package org.example.core

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.psi.KtNamedFunctionExtractor
import org.example.core.utils.Searcher
import org.example.data.reference.MethodCallReference
import org.example.data.symbol.ClassMethod
import org.example.data.symbol.ClassParameter
import org.example.data.symbol.ClassProperty
import org.example.data.symbol.KotlinClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext

class ClassReferenceBuilder : IClassReferenceBuilder {

    private lateinit var searcher: Searcher

    override fun bindAll(projectClasses: List<KotlinClass>) {

        searcher = Searcher(projectClasses)
        //Methods
        analyzeFunctionCalls(projectClasses)
        buildReverseCallRecords(projectClasses)

        //properties
        bindPropertyCalls(projectClasses)

        //parameter
        bindParameterCalls(projectClasses)
    }

    override fun bindMethodCalls(projectClasses: List<KotlinClass>) {
    }



    override fun bindPropertyCalls(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            val fieldsByName = cls.properties.associateBy { it.name ?: "__no_name__" }

            for (method in cls.functionCalls) {
                val fn = method.function
                val body = fn.bodyExpression ?: continue

                val nameRefs = body.collectDescendantsOfType<KtNameReferenceExpression>()

                for (ref in nameRefs) {
                    val propName = ref.getReferencedName()
                    val property = fieldsByName[propName] ?: continue

                    // Находим или создаём ClassProperty для этого свойства
                    val classProperty = cls.propertyReferences.firstOrNull { it.property == property }
                        ?: ClassProperty(property = property).also { cls.propertyReferences.add(it) }

                    // Добавляем ссылку на метод, который использует это свойство
                    classProperty.callRecord.add(
                        MethodCallReference(
                            fullName = method.fullName,
                            parentClass = cls
                        )
                    )
                }
            }
        }
    }

    override fun bindParameterCalls(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            // Индекс параметров по имени
            val paramsByName = cls.parameters.associateBy { it.name ?: "__no_name__" }

            for (method in cls.functionCalls) {
                val fn = method.function
                val body = fn.bodyExpression ?: continue

                val nameRefs = body.collectDescendantsOfType<KtNameReferenceExpression>()

                for (ref in nameRefs) {
                    val paramName = ref.getReferencedName()
                    val parameter = paramsByName[paramName] ?: continue

                    // Находим или создаём ClassParameter
                    val classParam = cls.parameterReferences.firstOrNull { it.property == parameter }
                        ?: ClassParameter(property = parameter).also { cls.parameterReferences.add(it) }

                    // Добавляем ссылку на метод, который использует этот параметр
                    classParam.callRecord.add(
                        MethodCallReference(
                            fullName = method.fullName,
                            parentClass = cls
                        )
                    )
                }
            }
        }
    }

    fun buildReverseCallRecords(projectClasses: List<KotlinClass>) {
        // 2. Очищаем обратные ссылки у всех методов
        projectClasses.flatMap { it.functionCalls }.forEach { it.reverseCallRecords.clear() }

        // 3. Проходим по каждому методу и его прямым вызовам
        for (cls in projectClasses) {
            for (method in cls.functionCalls) {
                for (call in method.callRecords) {

                    val calledMethod = searcher.finFullMethodName(call.fullName) ?: continue

                    // Добавляем текущий метод в reverseCallRecords вызываемого метода
                    calledMethod.reverseCallRecords.add(
                        MethodCallReference(
                            fullName = method.fullName,
                            parentClass = cls
                        )
                    )
                }
            }
        }
    }

    data class FunctionContent(
        val methodCalls: List<String>,
        val assignments: List<String>
    )

    fun parseFunctionContent(fn: KtNamedFunction): FunctionContent {

       val data = KtNamedFunctionExtractor.parseFunctionContent(fn)
        // --- Методы ---
        val methodCalls = fn.collectDescendantsOfType<KtCallExpression>().map { call ->
            val receiver = (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text
            val methodName = call.calleeExpression?.text ?: "unknown"

            // Аргументы
            val args = call.valueArguments.joinToString(", ") { arg ->
                arg.getArgumentExpression()?.text ?: "?"
            }

            val fullCall = if (receiver != null) "$receiver.$methodName($args)" else "$methodName($args)"
            fullCall
        }

        // --- Присваивания ---
        val assignments = fn.collectDescendantsOfType<KtBinaryExpression>()
            .filter { it.operationToken == KtTokens.EQ }
            .map { assign ->
                val left = assign.left?.text ?: "unknown"
                val right = assign.right?.text ?: "unknown"
                "$left = $right"
            }

        return FunctionContent(methodCalls = methodCalls, assignments = assignments)
    }

    fun analyzeFunctionCalls(projectClasses: List<KotlinClass>) {
        
        for (cls in projectClasses) {

            val classFields: Map<String, KtCallableDeclaration> =
                (cls.properties.asSequence().map { it as KtCallableDeclaration } +
                        cls.parameters.asSequence().map { it as KtCallableDeclaration })
                    .associateBy { it.name ?: "__no_name__" }




            for (method in cls.functionCalls) {
                val fn = method.function//funtion
                if(cls.name.contains( "NotificationService"))
                    parseFunctionContent(fn)
                // Находим все выражения вида a.b(), obj.service.doWork(), и т.д.
                val dotCalls = fn.collectDescendantsOfType<KtDotQualifiedExpression>()

                val debugList: List<String> = dotCalls.map { expr ->
                    val receiver = expr.receiverExpression.text
                    val selector = expr.selectorExpression?.text ?: "null"
                    "$receiver.$selector"
                }

                callLoop@ for (expr in dotCalls) {

                    val receiver = expr.receiverExpression as? KtNameReferenceExpression
                        ?: continue@callLoop

                    val selector = expr.selectorExpression as? KtCallExpression
                        ?: continue@callLoop

                    val receiverName = receiver.getReferencedName()//field name

                    // метод вызывается: a.method()
                    val calledMethodName = selector.calleeExpression?.text ?: continue@callLoop//fiel mat name

                    // что такое a ?
                    val fieldDecl = classFields[receiverName] ?: continue@callLoop

                    // какой у него тип?
                    val fieldType = fieldDecl.typeReference?.text ?: continue@callLoop

                    // находим класс по имени типа

                    val targetClass = searcher.findByClassName(fieldType) ?: continue@callLoop // --

                    // ----------- Строим полный ключ вызываемого метода -----------
                    // Параметры вызова (типов тут не узнать → Any)
                    val argParams = resolveArgumentTypes(selector, classFields, fn)

                    // Находим метод среди всех методов проекта
                    var targetMethod = searcher.findMethodByClassByMethodNameByParams(targetClass.name, calledMethodName, argParams)

                   if( targetMethod == null)
                       targetMethod = searcher.findFirstMethodByClassByMethodNameByParams(targetClass.name, calledMethodName)

                    targetMethod  ?: continue@callLoop



                    // Добавляем в callRecords текущего метода
                    method.callRecords.add(
                        MethodCallReference(
                            fullName = targetMethod.fullName,
                            parentClass = targetClass
                        )
                    )
                }
            }



            for (method in cls.functionCalls){
                val fn = method.function//funtion

                // Находим все выражения вида a.b(), obj.service.doWork(), и т.д.
                // ----------- 2. Вызовы методов текущего класса без this -----------
                // Берём только тело функции
                val body = fn.bodyExpression ?: continue
                val simpleCalls = body.collectDescendantsOfType<KtCallExpression>()
                    // исключаем аннотации
                    .filter { it.parent !is KtAnnotationEntry }

                for (callExpr in simpleCalls) {
                    // 1️⃣ только внутренние вызовы
                    val callee = callExpr.calleeExpression
                    if (callee !is KtNameReferenceExpression) continue

                    val calledMethodName = callee.text

                    var methodArgs = resolveArgumentTypes(callExpr, classFields, fn)

                    var targetMethod: ClassMethod? = null// searcher.finMethodByClassByMethodNameByParams(cls.name, calledMethodName, methodArgs)

                    if(targetMethod == null){
                        val methods =
                          searcher.findByClassNameByMethodName(cls.name, calledMethodName)

                        targetMethod = methods?.firstOrNull { it.parameters.size == methodArgs.size }
                    }

                    targetMethod  ?: continue

                    method.callRecords.add(
                        MethodCallReference(
                            fullName = targetMethod.fullName,
                            parentClass = cls
                        )
                    )
                }
            }
        }

    }

    fun resolveArgumentTypes(
        selector: KtCallExpression,
        allFields: Map<String, KtCallableDeclaration>,
        fn: KtNamedFunction
    ): List<String> {
        return selector.valueArguments.map { arg ->
            val expr = arg.getArgumentExpression()
            when (expr) {
                 is KtDotQualifiedExpression -> {
                    // Пример: EnvelopeStatus.SCHEDULED
                    val typeName = expr.receiverExpression.text
                    if (typeName.isNotBlank()) typeName else "Any"
                }

                is KtNameReferenceExpression -> {
                    val name = expr.getReferencedName()

                    // 1. Сначала проверяем параметры метода
                    val paramType = fn.valueParameters.find { it.name == name }?.typeReference?.text
                    if (paramType != null) return@map paramType

                    // 2. Проверяем поля класса
                    val fieldType = allFields[name]?.typeReference?.text
                    if (fieldType != null) return@map fieldType

                    // 3. Если не нашли — Any
                    val parent = expr.parent
                    if (parent is KtDotQualifiedExpression) {
                        val enumType = parent.receiverExpression.text
                        if (enumType.isNotBlank()) return@map enumType
                    }

                    // 4️⃣ По умолчанию Any
                    name // или "Any" если хочешь совсем безопасно
                }
                is KtCallExpression -> {
                    // Если передан вызов метода, пока можно использовать имя метода
                    expr.calleeExpression?.text ?: "Any"
                }
                is KtConstantExpression, is KtStringTemplateExpression -> {
                    // Литералы
                    when (expr) {
                        is KtConstantExpression -> expr.node.elementType.toString() // Int, Boolean и т.д.
                        else -> "String"
                    }
                }
                else -> "Any"
            }
        }
    }
}