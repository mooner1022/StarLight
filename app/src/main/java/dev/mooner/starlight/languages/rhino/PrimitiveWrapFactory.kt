package dev.mooner.starlight.languages.rhino

import dev.mooner.starlight.languages.ScriptHostPolicy
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.WrapFactory

/**
 * Passes strings, numbers and booleans to scripts as JavaScript values.
 *
 * When [restricted], Java objects and classes with members denied by [ScriptHostPolicy] are wrapped
 * so that scripts can't see those members. The class shutter from [restrictContext] covers the
 * classes denied as a whole.
 */
class PrimitiveWrapFactory(
    private val restricted: Boolean = ScriptHostPolicy.isEnabled,
): WrapFactory() {

    override fun wrap(cx: Context?, scope: Scriptable?, obj: Any?, staticType: Class<*>?): Any? {
        return when(obj) {
            null -> null
            is String, is Number, is Boolean -> obj
            !is Char -> super.wrap(cx, scope, obj, staticType)
            else -> String(charArrayOf(obj))
        }
    }

    override fun wrapAsJavaObject(cx: Context?, scope: Scriptable?, javaObject: Any?, staticType: Class<*>?): Scriptable =
        if (restricted && javaObject != null && ScriptHostPolicy.hasDeniedMembers(javaObject.javaClass))
            FilteredJavaObject(scope, javaObject, staticType)
        else
            super.wrapAsJavaObject(cx, scope, javaObject, staticType)

    override fun wrapJavaClass(cx: Context?, scope: Scriptable?, javaClass: Class<*>): Scriptable =
        if (restricted && ScriptHostPolicy.hasDeniedMembers(javaClass))
            FilteredJavaClass(scope, javaClass)
        else
            super.wrapJavaClass(cx, scope, javaClass)

    private class FilteredJavaObject(scope: Scriptable?, javaObject: Any, staticType: Class<*>?) :
        NativeJavaObject(scope, javaObject, staticType) {

        override fun has(name: String, start: Scriptable): Boolean =
            !isDenied(name) && super.has(name, start)

        override fun get(name: String, start: Scriptable): Any =
            if (isDenied(name)) Scriptable.NOT_FOUND else super.get(name, start)

        private fun isDenied(name: String): Boolean =
            isMemberDenied(javaObject.javaClass, name)
    }

    private class FilteredJavaClass(scope: Scriptable?, javaClass: Class<*>) : NativeJavaClass(scope, javaClass) {

        override fun has(name: String, start: Scriptable): Boolean =
            !isDenied(name) && super.has(name, start)

        override fun get(name: String, start: Scriptable): Any =
            if (isDenied(name)) Scriptable.NOT_FOUND else super.get(name, start)

        private fun isDenied(name: String): Boolean =
            isMemberDenied(classObject, name)
    }

    companion object {

        /**
         * Rhino also finds methods by signature, as in `System["load(java.lang.String)"]`, so only the
         * name before the signature is checked.
         */
        private fun isMemberDenied(cls: Class<*>, name: String): Boolean =
            ScriptHostPolicy.isMemberDenied(cls, name.substringBefore('('))

        private val shutter = ClassShutter { name -> !ScriptHostPolicy.isClassNameDenied(name) }

        /** Applies [ScriptHostPolicy] to a new context. */
        fun restrictContext(cx: Context) {
            cx.setClassShutter(shutter)
            cx.wrapFactory = PrimitiveWrapFactory(restricted = true)
        }
    }
}
