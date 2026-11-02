/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.script

import dev.mooner.neonjs.NeonValue
import java.math.BigInteger

/**
 * Reads values handed over by scripts, for both engines:
 * - NeonJS gives strings, numbers, booleans, BigInts and host objects (such as a Java `byte[]`) as Kotlin values, and
 *   JS arrays, objects and typed arrays as [NeonValue]s.
 * - Rhino gives JS arrays as `java.util.List`, objects as `java.util.Map`, Java objects unwrapped, and `undefined` as
 *   its Undefined.
 *
 * [NeonValue]s may only be read on the thread that called into the plugin, so callers convert everything they need
 * before handing work to another thread.
 */
object ScriptValues {

    private const val RHINO_UNDEFINED = "org.mozilla.javascript.Undefined"
    private const val RHINO_WRAPPER = "org.mozilla.javascript.Wrapper"

    /** Typed arrays above this size are refused: they are copied one element at a time. */
    const val MAX_TYPED_ARRAY_BYTES = 64L shl 20

    private val BYTE_VIEW_TAGS = setOf("[object Uint8Array]", "[object Int8Array]", "[object Uint8ClampedArray]")
    private const val ARRAY_BUFFER_TAG = "[object ArrayBuffer]"
    private const val DATA_VIEW_TAG = "[object DataView]"

    fun isAbsent(value: Any?): Boolean =
        value == null || value is Unit || value.javaClass.name == RHINO_UNDEFINED || (value is NeonValue && value.isNullish)

    /** Primitives as Kotlin values and host objects unwrapped; JS arrays and objects stay as they are. */
    fun unwrap(value: Any?): Any? =
        when {
            value == null -> null
            value is NeonValue -> when {
                value.isNullish    -> null
                value.isString     -> value.asString()
                value.isNumber     -> value.asDouble()
                value.isBoolean    -> value.asBoolean()
                value.isBigInt     -> value.asBigInteger()
                value.isHostObject -> value.asHostObject<Any?>()
                else               -> value
            }
            value is Unit || value.javaClass.name == RHINO_UNDEFINED -> null
            value.javaClass.implements(RHINO_WRAPPER) -> value.javaClass.getMethod("unwrap").invoke(value)
            else -> value
        }

    /** The elements of a JS array, a Java collection or a Java object array; null if [value] is none of them. */
    fun asList(value: Any?): List<Any?>? =
        when (val value = unwrap(value)) {
            is NeonValue    -> if (value.isArray) List(value.arraySize.toInt()) { i -> unwrap(value.getElement(i.toLong())) } else null
            is Collection<*> -> value.map(::unwrap)
            is Array<*>     -> value.map(::unwrap)
            else            -> null
        }

    /** A Java `byte[]`, or the bytes of a JS `Uint8Array`, `Int8Array`, `ArrayBuffer` or `DataView`; null otherwise. */
    fun asBytes(value: Any?): ByteArray? =
        when (val value = unwrap(value)) {
            is ByteArray -> value
            is NeonValue -> typedArrayBytes(value)
            else         -> null
        }

    /** Whether [value] is a JS object or a map, as options are given. */
    fun isObject(value: Any?): Boolean =
        when (val value = unwrap(value)) {
            is NeonValue -> value.isObject && !value.isArray && !value.isFunction
            is Map<*, *> -> true
            else         -> false
        }

    /** A property of a JS object or an entry of a map; null if absent. */
    fun member(obj: Any?, key: String): Any? =
        when (val obj = unwrap(obj)) {
            is NeonValue -> if (obj.isObject && obj.hasMember(key)) unwrap(obj.getMember(key)) else null
            is Map<*, *> -> unwrap(obj[key])
            else         -> null
        }

    /** The property names of a JS object or the keys of a map. */
    fun keys(obj: Any?): Set<String> =
        when (val obj = unwrap(obj)) {
            is NeonValue -> if (obj.isObject) obj.memberKeys() else emptySet()
            is Map<*, *> -> obj.keys.mapTo(linkedSetOf()) { it.toString() }
            else         -> emptySet()
        }

    /** The properties of a JS object or the entries of a map, as text. */
    fun stringEntries(obj: Any?): Map<String, String> =
        when (val obj = unwrap(obj)) {
            is NeonValue -> if (!obj.isObject) emptyMap() else obj.memberKeys()
                .mapNotNull { key -> toText(unwrap(obj.getMember(key)))?.let { key to it } }
                .toMap()
            is Map<*, *> -> obj.entries
                .mapNotNull { (key, value) -> toText(unwrap(value))?.let { key.toString() to it } }
                .toMap()
            else         -> emptyMap()
        }

    fun toText(value: Any?): String? =
        when (val value = unwrap(value)) {
            null            -> null
            is CharSequence -> value.toString()
            is Double       -> if (value % 1.0 == 0.0 && value in LONG_RANGE) value.toLong().toString() else value.toString()
            is Float        -> toText(value.toDouble())
            is Number, is Boolean, is Char -> value.toString()
            else            -> null
        }

    /** A number given as a number or numeric text; null if absent or not a number. */
    fun toLong(value: Any?): Long? =
        when (val value = unwrap(value)) {
            is Double       -> value.takeIf { it.isFinite() }?.toLong()
            is Float        -> value.takeIf { it.isFinite() }?.toLong()
            is BigInteger   -> value.toLong()
            is Number       -> value.toLong()
            is CharSequence -> value.toString().trim().toLongOrNull()
            else            -> null
        }

    fun toBoolean(value: Any?): Boolean? =
        when (val value = unwrap(value)) {
            is Boolean      -> value
            is CharSequence -> value.toString().trim().lowercase().toBooleanStrictOrNull()
            is Number       -> value.toDouble() != 0.0
            else            -> null
        }

    private fun typedArrayBytes(value: NeonValue): ByteArray? {
        if (!value.isObject || value.isArray || value.isFunction)
            return null
        val global = value.context.global
        val tag = runCatching {
            global.getMember("Object").getMember("prototype").getMember("toString").callWithThis(value).asString()
        }.getOrNull() ?: return null
        val view = when (tag) {
            in BYTE_VIEW_TAGS -> value
            ARRAY_BUFFER_TAG  -> global.getMember("Uint8Array").newInstance(value)
            DATA_VIEW_TAG     -> global.getMember("Uint8Array").newInstance(
                value.getMember("buffer"), value.getMember("byteOffset"), value.getMember("byteLength"),
            )
            else              -> return null
        }
        val size = view.getMember("length").asLong()
        require(size <= MAX_TYPED_ARRAY_BYTES) {
            "Typed array is too large (${size} bytes); pass a Java byte[] or a file instead"
        }
        return ByteArray(size.toInt()) { i -> view.getElement(i.toLong()).asInt().toByte() }
    }

    private fun Class<*>.implements(name: String): Boolean {
        var cls: Class<*>? = this
        while (cls != null) {
            if (cls.interfaces.any { it.name == name || it.implements(name) })
                return true
            cls = cls.superclass
        }
        return false
    }

    // Integers JS numbers hold exactly.
    private val LONG_RANGE = -9.007199254740991E15..9.007199254740991E15
}
