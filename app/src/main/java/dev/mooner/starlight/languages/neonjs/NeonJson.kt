/*
 * NeonJson.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.neonjs

import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.languages.ScriptJson

/**
 * JSON through a NeonJS context's own `JSON`. What [parse] gives is a [NeonValue], which becomes the JS value itself
 * when a host method returns it to that context; [stringify] takes the [NeonValue]s the context passes for JS objects.
 */
internal class NeonJson(private val context: NeonContext) : ScriptJson {

    // The script's global, as the module sees it: read when first needed, on the script's thread.
    private val json: NeonValue by lazy { context["JSON"] }

    private val arrays: NeonValue by lazy { context["Array"] }

    override fun parse(json: String): Any? =
        this.json.callMember("parse", json)

    override fun stringify(value: Any?): String? {
        val text = json.callMember("stringify", value)
        return if (text.isUndefined) null else text.asString()
    }

    // A Java array reaches the script as an array-like host object, which Array.from copies item by item.
    override fun array(items: Array<*>): Any? =
        arrays.callMember("from", items)
}
