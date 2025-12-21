package org.example.core.linking

import org.example.core.interfaces.IClassReferenceBuilder
import org.example.core.interfaces.IProjectSearchEngine
import org.example.core.psi.KtFunctionExpressionCollector
import org.example.data.reference.MethodCallReference
import org.example.data.symbol.KotlinClass
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class ClassReferenceBuilder (): IClassReferenceBuilder {


    private lateinit var searchEngine: IProjectSearchEngine

    override fun bindAll(projectClasses: List<KotlinClass>, searchEngine: IProjectSearchEngine) {

        this.searchEngine = searchEngine


        // 11 collect function expressions
        collectExpressions(projectClasses)

        //Methods
        buildFunctionCallRecords(projectClasses)
        buildFunctionReverseCallRecords(projectClasses)

        //properties
        bindPropertyCalls(projectClasses)

        //parameter
        bindParameterCalls(projectClasses)
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

    fun buildFunctionCallRecords(projectClasses: List<KotlinClass>) {

    }

    fun buildFunctionReverseCallRecords(projectClasses: List<KotlinClass>) {

    }

    fun bindPropertyCalls(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            val fieldsByName = cls.ktProperties.associateBy { it.name ?: "__no_name__" }

            for (method in cls.functionCalls) {
                val fn = method.function
                val body = fn.bodyExpression ?: continue

                val nameRefs = body.collectDescendantsOfType<KtNameReferenceExpression>()

                for (ref in nameRefs) {
                    val propName = ref.getReferencedName()
                    val property = fieldsByName[propName] ?: continue

                    // Находим или создаём ClassProperty для этого свойства
                    val classProperty = cls.propertyReferences.firstOrNull { it.property == property }
                        ?: continue//ClassProperty(property = property).also { cls.propertyReferences.add(it) }

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

    fun bindParameterCalls(projectClasses: List<KotlinClass>) {
        for (cls in projectClasses) {
            // Индекс параметров по имени
            val paramsByName = cls.ktParameters.associateBy { it.name ?: "__no_name__" }

            for (method in cls.functionCalls) {
                val fn = method.function
                val body = fn.bodyExpression ?: continue

                val nameRefs = body.collectDescendantsOfType<KtNameReferenceExpression>()

                for (ref in nameRefs) {
                    val paramName = ref.getReferencedName()
                    val parameter = paramsByName[paramName] ?: continue

                    // Находим или создаём ClassParameter
                    val classParam = cls.parameterReferences.firstOrNull { it.property == parameter }
                        ?: continue//ClassParameter(property = parameter).also { cls.parameterReferences.add(it) }

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

                    val calledMethod = searchEngine.findByFullMethodName(call.fullName) ?: continue

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