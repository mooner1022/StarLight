package dev.mooner.starlight.plugincore.api

import dev.mooner.starlight.plugincore.project.Project

abstract class Api <T> {

    /*
    private var _project: Project? = null

    protected val project: Project
        get() = _project!!

    internal fun setProject(project: Project) {
        _project = project
    }
     */

    protected class ApiFunctionBuilder {

        var name: String? = null
        var args: Array<Class<*>> = emptyArray()
        var returns: Class<*> = Unit::class.java

        @Suppress("SameParameterValue")
        private fun required(fieldName: String, value: Any?) {
            if (value == null) {
                throw IllegalArgumentException("Required field '$fieldName' is null")
            }
        }

        fun build(): ApiFunction {
            required("name", name)

            return ApiFunction(
                name = name!!,
                args = args,
                returns = returns
            )
        }
    }

    protected class ApiValueBuilder {

        var name: String? = null
        var returns: Class<*> = Unit::class.java

        @Suppress("SameParameterValue")
        private fun required(fieldName: String, value: Any?) {
            if (value == null) {
                throw IllegalArgumentException("Required field '$fieldName' is null")
            }
        }

        fun build(): ApiValue {
            required("name", name)

            return ApiValue(
                name = name!!,
                returns = returns
            )
        }
    }

    protected fun function(block: ApiFunctionBuilder.() -> Unit): ApiFunction {
        val builder = ApiFunctionBuilder().apply(block)
        return builder.build()
    }

    protected fun value(block: ApiValueBuilder.() -> Unit): ApiValue {
        val builder = ApiValueBuilder().apply(block)
        return builder.build()
    }

    abstract val name: String

    abstract val objects: List<ApiObject>

    abstract val instanceClass: Class<T>

    abstract val instanceType: InstanceType

    abstract fun getInstance(project: Project): Any
}