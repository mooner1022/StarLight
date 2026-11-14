/*
 * BridgeApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.legacy

import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.languages.neonjs.whileWaitingForAnotherThread
import dev.mooner.starlight.languages.rhino.JSRhino
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

@Suppress("unused")
class BridgeApi: Api<BridgeApi.Bridge>() {

    /**
     * The `Bridge` global of legacy projects: reaches the functions and variables of other compiled projects.
     *
     * A project is named by its name, with or without a trailing `.js`. Every member that reaches into a project
     * gives `null` (`false` for `hasVariableOf` and `setVariableOf`) when no compiled project has that name; a name
     * that isn't a string throws `scriptName must be a string`, except in `setVariableOf` and `getMemberNamesOf`, which
     * never throw. Every project allows Bridge access.
     *
     * Rhino projects expose their global scope: every top-level `var` and `function`. A NeonJS project is an ES
     * module, so only what it `export`s can be reached, and `setVariableOf` can't change it.
     *
     * @see guide:projects#calling-other-projects
     */
    class Bridge internal constructor(
        private val project: Project,
        private val json: ScriptJson,
    ) {

        /**
         * Calls the function [functionName] of the project [scriptName] and returns what it returned.
         *
         * Any number of arguments follow the two names. `undefined` arrives in the other project as `null`, from a
         * Rhino project too, where the shared `undefined` is told from the text `'undefined'` by identity. The call waits
         * for the other project's code with this project's own NeonJS context released.
         *
         * ```js
         * const sum = Bridge.callFunctionOf("math", "add", 2, 3);
         * ```
         *
         * @param scriptName The project's name, with or without `.js`.
         * @param functionName The name of a function in that project's scope.
         * @return The function's result, or `null` when the project isn't compiled or has no such function.
         * @throws IllegalArgumentException When a name isn't a string.
         * @throws RuntimeException `Failed to call function '<name>' in script '<project>': <message>` when the
         * function throws.
         */
        @Suppress("UNCHECKED_CAST")
        fun callFunctionOf(scriptName: Any?, functionName: Any?, vararg args: Any?): Any? {
            val name = nameOf(scriptName)
            val function = ScriptArgs.string(functionName, "functionName")
            val target = reachable(name) ?: return null
            try {
                val scope = target.getScope() ?: return null
                val passed = Array(args.size) { toTarget(args[it]) }
                return whileWaitingForAnotherThread {
                    when (scope) {
                        is ScriptableObject -> inRhino(target) { cx ->
                            val callee = ScriptableObject.getProperty(scope, function)
                            if (callee is org.mozilla.javascript.Function)
                                callee.call(cx, scope, scope, passed)
                            else
                                null
                        }
                        else -> target.getLanguage().callFunction(scope, function, passed as Array<out Any>)
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to call function '$function' in script '$name': ${e.message}", e)
            }
        }

        /**
         * Runs [code] in the global scope of the project [scriptName] and returns its last value.
         *
         * Variables and functions the code declares with `var` and `function` become part of that project's scope.
         * In a NeonJS project the code runs as a plain script next to the module, so it can't see the module's own
         * top-level declarations.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @param code The JavaScript source to run.
         * @return The value of the code's last expression, or `null` when the project isn't compiled.
         * @throws IllegalArgumentException When [scriptName] isn't a string.
         * @throws RuntimeException `Failed to evaluate code in script '<project>': <message>` when the code is invalid
         * or throws.
         */
        fun evalInScript(scriptName: Any?, code: Any?): Any? {
            val name = nameOf(scriptName)
            val target = reachable(name) ?: return null
            try {
                val source = ScriptArgs.string(code, "code")
                val scope = target.getScope() ?: return null
                return whileWaitingForAnotherThread {
                    when (scope) {
                        is ScriptableObject -> inRhino(target) { cx ->
                            cx.evaluateString(scope, source, "<bridge>", 1, null)
                        }
                        is NeonValue        -> fromNeon(scope.context.eval(source, "<bridge>"))
                        else                -> null
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to evaluate code in script '$name': ${e.message}", e)
            }
        }

        /**
         * The names of all projects that are compiled now, this one included.
         *
         * @return An array of project names, in no particular order; `null` when they can't be listed.
         */
        fun getActiveScripts(): Any? =
            try {
                val names = Session.projectManager.getProjects()
                    .filter { it.isCompiled }
                    .map { it.info.name }
                json.parse(JsonArray(names.map(::JsonPrimitive)).toString())
            } catch (_: Exception) {
                null
            }

        /**
         * The names of the variables and functions in the scope of the project [scriptName].
         *
         * For a NeonJS project they are its exports.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return An array of names, or `null` when the project isn't compiled or anything fails.
         */
        fun getMemberNamesOf(scriptName: Any?): Any? =
            try {
                val target = reachable(nameOf(scriptName)) ?: return null
                val scope = target.getScope()
                val names: List<String>? = whileWaitingForAnotherThread {
                    when (scope) {
                        is ScriptableObject -> inRhino(target) { scope.ids.filterIsInstance<String>() }
                        is NeonValue        -> scope.memberKeys().toList()
                        else                -> null
                    }
                }
                names?.let { json.parse(JsonArray(it.map(::JsonPrimitive)).toString()) }
            } catch (_: Exception) {
                null
            }

        /**
         * The global scope of the project [scriptName], whose variables and functions are its properties.
         *
         * Read and call them as properties: `Bridge.getScopeOf("other").count`, `Bridge.getScopeOf("other").greet()`.
         * A Rhino project's scope is live: writing a property changes the project's variable. A NeonJS project's
         * scope holds its exports, and only a Rhino project can read it; from a NeonJS project the result is `null`.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @return The scope, or `null` when the project isn't compiled or its scope isn't reachable from this project.
         * @throws IllegalArgumentException When [scriptName] isn't a string.
         */
        fun getScopeOf(scriptName: Any?): Any? {
            val target = reachable(nameOf(scriptName)) ?: return null
            return when (val scope = target.getScope()) {
                is ScriptableObject -> scope
                is NeonValue -> {
                    val own = project.getScope()
                    if (own is Scriptable) NeonScope(scope, own) else null
                }
                else -> null
            }
        }

        /**
         * The value of the variable or function [variableName] in the project [scriptName].
         *
         * @param scriptName The project's name, with or without `.js`.
         * @param variableName The variable's name.
         * @return The value, or `null` when the project isn't compiled or has no such variable.
         * @throws IllegalArgumentException When a name isn't a string.
         */
        fun getVariableOf(scriptName: Any?, variableName: Any?): Any? {
            val name = nameOf(scriptName)
            val variable = ScriptArgs.string(variableName, "variableName")
            val target = reachable(name) ?: return null
            val scope = target.getScope()
            return whileWaitingForAnotherThread {
                when (scope) {
                    is ScriptableObject -> inRhino(target) {
                        if (ScriptableObject.hasProperty(scope, variable))
                            ScriptableObject.getProperty(scope, variable).takeIf { it !== Scriptable.NOT_FOUND }
                        else
                            null
                    }
                    is NeonValue -> if (scope.hasMember(variable)) fromNeon(scope.getMember(variable)) else null
                    else -> null
                }
            }
        }

        /**
         * Whether the project [scriptName] has a variable or function named [variableName].
         *
         * @param scriptName The project's name, with or without `.js`.
         * @param variableName The variable's name.
         * @return `false` also when the project isn't compiled.
         * @throws IllegalArgumentException When a name isn't a string.
         */
        fun hasVariableOf(scriptName: Any?, variableName: Any?): Boolean {
            val name = nameOf(scriptName)
            val variable = ScriptArgs.string(variableName, "variableName")
            val target = reachable(name) ?: return false
            val scope = target.getScope()
            return whileWaitingForAnotherThread {
                when (scope) {
                    is ScriptableObject -> inRhino(target) { ScriptableObject.hasProperty(scope, variable) }
                    is NeonValue        -> scope.hasMember(variable)
                    else                -> false
                }
            }
        }

        /**
         * Whether the project [scriptName] lets other projects reach it through `Bridge`.
         *
         * Every project does, so this is `true` for any name, existing or not; use [isScriptActive] to find out
         * whether a project is compiled.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @throws IllegalArgumentException When [scriptName] isn't a string.
         */
        fun isAllowed(scriptName: Any?): Boolean {
            nameOf(scriptName)
            return true
        }

        /**
         * Whether the project [scriptName] is compiled, so that its functions and variables can be reached.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @throws IllegalArgumentException When [scriptName] isn't a string.
         */
        fun isScriptActive(scriptName: Any?): Boolean =
            compiled(nameOf(scriptName)) != null

        /**
         * Sets the variable [variableName] of the project [scriptName] to [value], creating it when it doesn't exist.
         *
         * `undefined` is stored as `null`, from a Rhino project too. A NeonJS project's exports can't be changed, so there it gives `false`.
         *
         * @param scriptName The project's name, with or without `.js`.
         * @param variableName The variable's name.
         * @param value The new value.
         * @return `true` when it was set; `false` when the project isn't compiled, a name isn't a string, or setting
         * failed. It never throws.
         */
        fun setVariableOf(scriptName: Any?, variableName: Any?, value: Any?): Boolean =
            try {
                val name = nameOf(scriptName)
                val variable = ScriptArgs.string(variableName, "variableName")
                val target = reachable(name) ?: return false
                val scope = target.getScope()
                val stored = toTarget(value)
                whileWaitingForAnotherThread {
                    when (scope) {
                        is ScriptableObject -> {
                            inRhino(target) { ScriptableObject.putProperty(scope, variable, stored) }
                            true
                        }
                        is NeonValue -> {
                            scope.putMember(variable, stored)
                            true
                        }
                        else -> false
                    }
                }
            } catch (_: Exception) {
                false
            }

        private fun nameOf(value: Any?): String =
            ScriptArgs.string(value, "scriptName").removeSuffix(".js")

        private fun compiled(name: String): Project? =
            Session.projectManager.getProjectByName(name)
                ?.takeIf { it.isCompiled && it.getScope() != null }

        /** The compiled project [name], when it allows Bridge access, which every project does. */
        private fun reachable(name: String): Project? =
            compiled(name)

        /** An argument as the other project should see it: `undefined` as `null`, a NeonJS primitive as plain. */
        private fun toTarget(value: Any?): Any? = when {
            ScriptArgs.isMissing(value) -> null
            value is NeonValue -> when {
                value.isBoolean -> value.asBoolean()
                value.isNumber  -> value.asDouble()
                value.isString  -> value.asString()
                else            -> value
            }
            else -> value
        }
    }

    override val name: String = "Bridge"

    override val objects: List<ApiObject> = listOf(
        function {
            name = "callFunctionOf"
            args = arrayOf(String::class.java, String::class.java, Array<Any>::class.java)
            returns = Any::class.java
        },
        function {
            name = "evalInScript"
            args = arrayOf(String::class.java, String::class.java)
            returns = Any::class.java
        },
        function {
            name = "getActiveScripts"
            returns = Any::class.java
        },
        function {
            name = "getMemberNamesOf"
            args = arrayOf(String::class.java)
            returns = Any::class.java
        },
        function {
            name = "getScopeOf"
            args = arrayOf(String::class.java)
            returns = Any::class.java
        },
        function {
            name = "getVariableOf"
            args = arrayOf(String::class.java, String::class.java)
            returns = Any::class.java
        },
        function {
            name = "hasVariableOf"
            args = arrayOf(String::class.java, String::class.java)
            returns = Boolean::class.java
        },
        function {
            name = "isAllowed"
            args = arrayOf(String::class.java)
            returns = Boolean::class.java
        },
        function {
            name = "isScriptActive"
            args = arrayOf(String::class.java)
            returns = Boolean::class.java
        },
        function {
            name = "setVariableOf"
            args = arrayOf(String::class.java, String::class.java, Any::class.java)
            returns = Boolean::class.java
        }
    )

    override val instanceClass: Class<Bridge> = Bridge::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = Bridge(project, ScriptJson.current())
}

/** Runs [block] with a Rhino context entered on this thread, set up as the project's language sets it up. */
private fun <T> inRhino(project: Project, block: (Context) -> T): T {
    val language = project.getLanguage()
    val context = if (language is JSRhino) language.enterContext() else Context.enter()
    try {
        return block(context)
    } finally {
        Context.exit()
    }
}

/** A value of a NeonJS project as a plain one: primitives as they are, `null` for `null` and `undefined`. */
private fun fromNeon(value: NeonValue): Any? = when {
    value.isNullish -> null
    value.isBoolean -> value.asBoolean()
    value.isNumber  -> value.asDouble()
    value.isString  -> value.asString()
    else            -> value.`as`(Any::class.java)
}

/**
 * A NeonJS project's exports as a Rhino object, so that a Rhino project can read, write and call them as properties.
 */
private class NeonScope(
    private val module: NeonValue,
    caller: Scriptable,
) : ScriptableObject() {

    init {
        ScriptRuntime.setObjectProtoAndParent(this, caller)
    }

    override fun getClassName(): String = "Object"

    override fun has(name: String, start: Scriptable): Boolean = module.hasMember(name)

    override fun get(name: String, start: Scriptable): Any? {
        if (!module.hasMember(name))
            return Scriptable.NOT_FOUND
        val member = module.getMember(name)
        return if (member.isFunction) NeonFunction(member, this) else fromNeon(member)
    }

    override fun put(name: String, start: Scriptable, value: Any?) {
        module.putMember(name, if (ScriptArgs.isMissing(value)) null else value)
    }

    override fun getIds(): Array<Any> =
        module.memberKeys().toTypedArray<Any>()
}

private class NeonFunction(
    private val function: NeonValue,
    scope: Scriptable,
) : BaseFunction() {

    init {
        ScriptRuntime.setFunctionProtoAndParent(this, scope)
    }

    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
        val passed = Array<Any?>(args.size) { if (ScriptArgs.isMissing(args[it])) null else args[it].let { a -> if (a is CharSequence) a.toString() else a } }
        return fromNeon(function.call(*passed))
    }
}
