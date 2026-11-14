/*
 * ScriptArgs.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonValue
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.math.BigInteger

/**
 * How the MessengerBot-compatible objects read what scripts pass them.
 *
 * MessengerBot R takes most arguments as any value and converts them itself. A number where text is expected becomes its
 * text. The strings `"true"` and `"false"` count as booleans. A missing optional value takes its default. These do the
 * same with what StarLight's engines pass to a parameter of type `Any?`:
 * - Rhino passes its own values: `Undefined`, `Double` numbers, `CharSequence` strings, `Scriptable` objects and
 *   arrays, the arrays as `List`.
 * - NeonJS passes `null` for both `null` and `undefined`, `Int`/`Long`/`Double` numbers, `String`s, `Boolean`s, and
 *   [NeonValue] for objects, arrays and functions.
 *
 * Every error message reads as MessengerBot's: `"<name> must be a string"`, `"<name> must be a number"`,
 * `"<name> must be a boolean"`.
 */
internal object ScriptArgs {

    /**
     * Whether [value] is JS `null` or `undefined`, as either engine passes it to a parameter that takes text.
     *
     * Rhino's `undefined` there is the text `"undefined"` and stays text: a room or a message may well be that word, as
     * the debug room's own name is until it is set, and so is what `typeof` gives. Where a value can't be text, as an
     * optional callback, use [isMissing].
     */
    fun isNullish(value: Any?): Boolean =
        value == null || value === Undefined.instance || value === Scriptable.NOT_FOUND ||
            (value is NeonValue && value.isNullish)

    /**
     * [isNullish], and Rhino's `undefined` as well: for a parameter that can't take text, such as an optional callback,
     * a boolean or a number, or a value handed on to another script, where MessengerBot sees `null` for `undefined`.
     */
    fun isMissing(value: Any?): Boolean =
        isNullish(value) || isUndefined(value)

    /**
     * Whether [value] is JS `undefined`, as far as the engine that passed it tells.
     *
     * Rhino hands a parameter of type `Any?` the text `"undefined"` for `undefined`: the JVM's one shared instance of
     * that text, so it is told apart by identity, and only for a call from Rhino. A string a script builds is another
     * instance and stays text; text Rhino itself made, as what `typeof` gives, is that instance and counts as
     * `undefined`. NeonJS passes `undefined` and `null` alike as `null`, so there `undefined` can't be told from
     * `null` and this is `false`; a NeonJS string `'undefined'` may well be that shared instance, and is still text.
     */
    @Suppress("StringEquality")
    fun isUndefined(value: Any?): Boolean =
        value === Undefined.instance || value === Scriptable.NOT_FOUND ||
            (value === RHINO_UNDEFINED_TEXT && isRhinoCall()) ||
            (value is NeonValue && value.isUndefined && !value.isNull)

    /** Whether the host code running now was called by a Rhino script, not a NeonJS one. */
    private fun isRhinoCall(): Boolean =
        NeonContext.current() == null && Context.getCurrentContext() != null

    /**
     * [value] as text, as MessengerBot writes a value it prints, stores or sends.
     *
     * - `null` and `undefined` become `"null"`; in Rhino an `undefined` passed straight to the API is the text
     *   `"undefined"` (see [isNullish]).
     * - Strings are as they are.
     * - Whole numbers have no fraction (`5`). Other numbers are written as Java writes a `double` (`1.5`, `1.0E21`).
     * - Arrays become their items' text, joined as `[a, b]`.
     * - Other objects become what the engine's `String(value)` gives.
     */
    fun stringify(value: Any?): String = when {
        isNullish(value)      -> "null"
        value is CharSequence -> value.toString()
        value is Boolean      -> value.toString()
        value is Number       -> numberText(value)
        value is NeonValue    -> neonText(value)
        value is List<*>      -> value.joinToString(", ", "[", "]", transform = ::stringify)
        value is Array<*>     -> value.joinToString(", ", "[", "]", transform = ::stringify)
        value is Scriptable   -> rhinoText(value)
        else                  -> value.toString()
    }

    /**
     * [value] as text for a parameter named [name] that needs one.
     *
     * @throws IllegalArgumentException `"<name> must be a string"` when [value] is `null` or `undefined`.
     */
    fun string(value: Any?, name: String): String {
        if (isNullish(value))
            throw IllegalArgumentException("$name must be a string")
        return stringify(value)
    }

    /**
     * [value] as a name for a parameter named [name] that can't sensibly be the word `undefined`, such as an event
     * name: as [string], and Rhino's `undefined` throws too (see [isMissing]).
     *
     * @throws IllegalArgumentException `"<name> must be a string"` when [value] is `null` or `undefined`.
     */
    fun identifier(value: Any?, name: String): String {
        if (isMissing(value))
            throw IllegalArgumentException("$name must be a string")
        return stringify(value)
    }

    /** [value] to hand on to another script: `null` for `null` and `undefined`, Rhino's included; else as it is. */
    fun handOn(value: Any?): Any? =
        if (isMissing(value)) null else value

    /** [value] as text, or `null` when it is `null` or `undefined`. */
    fun stringOrNull(value: Any?): String? =
        if (isNullish(value)) null else stringify(value)

    /**
     * [value] as a boolean for a parameter named [name].
     *
     * - A boolean is as it is.
     * - A number is `true` unless it is `0`.
     * - Only the exact strings `"true"` and `"false"` count; any other string gives [default].
     * - `null` and `undefined` give [default].
     *
     * @throws IllegalArgumentException `"<name> must be a boolean"` for any other value, such as an object.
     */
    fun boolean(value: Any?, name: String, default: Boolean): Boolean {
        val raw = unwrap(value)
        return when {
            isMissing(raw)      -> default
            raw is Boolean      -> raw
            raw is Number       -> raw.toDouble() != 0.0
            raw is CharSequence -> when (raw.toString()) {
                "true"  -> true
                "false" -> false
                else    -> default
            }
            else -> throw IllegalArgumentException("$name must be a boolean")
        }
    }

    /**
     * [value] as an `Int` for a parameter named [name].
     *
     * A number is cut to a whole one (`1.9` is `1`). A string must be a whole number (`"12"`). When [value] is `null`,
     * `undefined` or not a number, [default] is used.
     *
     * @throws IllegalArgumentException `"<name> must be a number"` when there is no [default] to use.
     */
    fun int(value: Any?, name: String, default: Int? = null): Int =
        number(value, name, default, String::toIntOrNull) { it.toInt() }

    /** [int] for a `Long`. */
    fun long(value: Any?, name: String, default: Long? = null): Long =
        number(value, name, default, String::toLongOrNull) { it.toLong() }

    /** [int] for a `Float`; a string may have a fraction (`"1.5"`). */
    fun float(value: Any?, name: String, default: Float? = null): Float =
        number(value, name, default, String::toFloatOrNull) { it.toFloat() }

    /**
     * The whole number [value] holds, as a room ID or message ID: a BigInt, or a number with no fraction. `null` for
     * anything else, strings included, since a string names a room rather than numbering it.
     */
    fun integral(value: Any?): BigInteger? = when (val raw = unwrap(value)) {
        is BigInteger                  -> raw
        is Long, is Int, is Short, is Byte -> BigInteger.valueOf((raw as Number).toLong())
        is Double, is Float            -> raw.toDouble().takeIf { it.isFinite() && it == Math.floor(it) }
                                              ?.let { BigInteger.valueOf(it.toLong()) }
        else                           -> null
    }

    /** Whether [value] is a script function either engine passes. */
    fun isFunction(value: Any?): Boolean =
        value is org.mozilla.javascript.Function || (value is NeonValue && value.isFunction) || value is Function<*>

    /**
     * The plain value inside a [NeonValue] that holds a primitive, so the conversions above see a number, string or
     * boolean whichever engine passed it; anything else as it is.
     */
    private fun unwrap(value: Any?): Any? = when {
        value !is NeonValue -> value
        value.isNullish     -> null
        value.isBoolean     -> value.asBoolean()
        value.isNumber      -> value.asDouble()
        value.isString      -> value.asString()
        value.isBigInt      -> value.asBigInteger()
        else                -> value
    }

    private fun <T : Any> number(
        value: Any?,
        name: String,
        default: T?,
        parse: (String) -> T?,
        convert: (Number) -> T,
    ): T {
        val raw = unwrap(value)
        val result = when (raw) {
            is Number       -> convert(raw)
            is CharSequence -> parse(raw.toString().trim())
            else            -> null
        }
        return result ?: default ?: throw IllegalArgumentException("$name must be a number")
    }

    private fun numberText(number: Number): String = when (number) {
        is Double, is Float -> {
            val d = number.toDouble()
            if (d.isFinite() && d == Math.floor(d) && Math.abs(d) <= MAX_SAFE_INTEGER) d.toLong().toString()
            else d.toString()
        }
        else -> number.toString()
    }

    private fun neonText(value: NeonValue): String {
        unwrap(value).let { if (it !is NeonValue) return stringify(it) }
        if (value.isArray)
            return (0 until value.arraySize).joinToString(", ", "[", "]") { stringify(value.getMember(it.toString())) }
        return value.toString()
    }

    private fun rhinoText(value: Scriptable): String =
        try {
            if (Context.getCurrentContext() != null) ScriptRuntime.toString(value) else value.toString()
        } catch (_: RuntimeException) {
            value.toString()
        }

    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0

    /** What Rhino passes for `undefined`: this text, as the JVM's one interned instance of it. */
    private const val RHINO_UNDEFINED_TEXT = "undefined"
}
