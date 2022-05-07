package dev.mooner.starlight.languages.rhino

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.WrapFactory

class PrimitiveWrapFactory: WrapFactory() {

    override fun wrap(cx: Context?, scope: Scriptable?, obj: Any?, staticType: Class<*>?): Any? {
        return when(obj) {
            null -> null
            is String, is Number, is Boolean -> obj
            !is Char -> super.wrap(cx, scope, obj, staticType)
            else -> String(charArrayOf(obj))
        }
    }
}