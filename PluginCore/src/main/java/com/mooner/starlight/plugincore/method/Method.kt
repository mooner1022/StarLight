package com.mooner.starlight.plugincore.method

abstract class Method <T>: IMethod<T> {

    /*
    private var _project: Project? = null

    protected val project: Project
        get() = _project!!

    internal fun setProject(project: Project) {
        _project = project
    }
     */

    protected class MethodFunctionBuilder {

        var name: String? = null
        var args: Array<Class<*>> = emptyArray()
        var returns: Class<*> = Unit::class.java

        private fun required(fieldName: String, value: Any?) {
            if (value == null) {
                throw IllegalArgumentException("Required field '$fieldName' is null")
            }
        }

        fun build(): MethodFunction {
            required("name", name)

            return MethodFunction(
                name = name!!,
                args = args,
                returns = returns
            )
        }
    }

    protected fun function(block: MethodFunctionBuilder.() -> Unit): MethodFunction {
        val builder = MethodFunctionBuilder().apply(block)
        return builder.build()
    }
}