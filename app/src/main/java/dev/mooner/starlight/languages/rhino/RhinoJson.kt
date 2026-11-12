/*
 * RhinoJson.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.rhino

import dev.mooner.starlight.languages.ScriptJson
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * JSON through the `JSON` of a Rhino project's top-level scope. Rhino returns the objects [parse] makes to the script as
 * they are, and passes JS objects to host methods as themselves, which [stringify] hands back to `JSON.stringify`.
 */
internal class RhinoJson(private val scope: Scriptable) : ScriptJson {

    override fun parse(json: String): Any? =
        call("parse", json)

    override fun stringify(value: Any?): String? =
        call("stringify", value)
            .takeUnless { it == null || it is Undefined }
            ?.let(Context::toString)

    private fun call(name: String, argument: Any?): Any? {
        // A script calling in, or a callback Rhino runs on another thread, has entered a context.
        val cx = Context.getCurrentContext()
            ?: throw IllegalStateException("JSON.$name needs the script's thread")
        val json = ScriptableObject.getProperty(scope, "JSON") as Scriptable
        val function = ScriptableObject.getProperty(json, name) as Function
        return function.call(cx, scope, json, arrayOf(Context.javaToJS(argument, scope)))
    }
}
