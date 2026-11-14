/*
 * RhinoFunctions.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.rhino

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

/**
 * A function of the script's scope that Kotlin implements, taking every argument as it is passed, however many.
 *
 * [body] gives the result; `Unit` stands for no value, which the script sees as `undefined`, and `null` is JS `null`.
 * Make it after the standard objects exist in [scope], as its prototype is theirs.
 */
internal class RhinoFunction(
    scope: Scriptable,
    private val functionName: String,
    private val arity: Int,
    private val body: (cx: Context, args: Array<Any?>) -> Any?,
) : BaseFunction() {

    init {
        ScriptRuntime.setFunctionProtoAndParent(this, scope)
    }

    override fun getFunctionName(): String = functionName

    override fun getArity(): Int = arity

    override fun getLength(): Int = arity

    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
        val result = body(cx, args)
        return if (result === Unit) Undefined.instance else result
    }
}
