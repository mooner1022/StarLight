/*
 * AppDataApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import android.content.Context
import android.content.SharedPreferences
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.media.script.ScriptValues
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

@Suppress("unused")
class AppDataApi: Api<AppDataApi.AppData>() {

    /**
     * The `AppData` global: key-value storage that every project of this device shares and that stays across restarts;
     * a global in legacy and API2 projects.
     *
     * Every value keeps the type it was written with: `putInt` and `getInt` go together, and reading a key as another
     * type than it was written as (`getLong` after `putInt`, or `getString` after `putBoolean`) throws an error. A key
     * can be any value that has a text: a number is used as its text. `null` and `undefined` throw
     * `"key must be a string"`.
     *
     * Changes are visible at once and saved in the background. Every project reads and writes the same keys, so choose
     * distinctive ones; `clear()` removes the keys of all projects. For values that belong to one project, `Store` is
     * the better fit.
     */
    class AppData internal constructor(private val json: ScriptJson) {

        private val prefs: SharedPreferences
            get() = GlobalApplication.requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun keyOf(value: Any?): String =
            ScriptArgs.string(value, "key")

        /**
         * The text stored under [key].
         *
         * @param key The key.
         * @param defaultValue What to return when [key] has no value, as text: a number is its text, `null` is `"null"`.
         * `""` when left out.
         * @throws ClassCastException When the value isn't text.
         */
        @JvmOverloads
        fun getString(key: Any?, defaultValue: Any? = ""): String =
            prefs.getString(keyOf(key), ScriptArgs.stringify(defaultValue))!!

        /**
         * Stores [value] as text under [key].
         *
         * @param key The key.
         * @param value What to store, as text: a number is its text, `null` is `"null"`.
         */
        fun putString(key: Any?, value: Any?) {
            prefs.edit().putString(keyOf(key), ScriptArgs.stringify(value)).apply()
        }

        /**
         * The whole number stored under [key].
         *
         * @param key The key.
         * @param defaultValue What to return when [key] has no value. `0` when left out, or when it isn't a number.
         * @throws ClassCastException When the value wasn't stored by `putInt`.
         */
        @JvmOverloads
        fun getInt(key: Any?, defaultValue: Any? = 0): Int =
            prefs.getInt(keyOf(key), ScriptArgs.int(defaultValue, "defaultValue", 0))

        /**
         * Stores the whole number [value] under [key].
         *
         * @param key The key.
         * @param value A number, cut to a whole one, or a text of one.
         * @throws IllegalArgumentException `"integer must be a number"` when [value] isn't a number.
         */
        fun putInt(key: Any?, value: Any?) {
            val k = keyOf(key)
            prefs.edit().putInt(k, ScriptArgs.int(value, "integer")).apply()
        }

        /**
         * The large whole number stored under [key].
         *
         * @param key The key.
         * @param defaultValue What to return when [key] has no value. `0` when left out, or when it isn't a number.
         * @throws ClassCastException When the value wasn't stored by `putLong`; `putInt`'s doesn't count.
         */
        @JvmOverloads
        fun getLong(key: Any?, defaultValue: Any? = 0L): Long =
            prefs.getLong(keyOf(key), ScriptArgs.long(defaultValue, "defaultValue", 0L))

        /**
         * Stores the large whole number [value] under [key].
         *
         * @param key The key.
         * @param value A number, cut to a whole one, or a text of one.
         * @throws IllegalArgumentException `"long must be a number"` when [value] isn't a number.
         */
        fun putLong(key: Any?, value: Any?) {
            val k = keyOf(key)
            prefs.edit().putLong(k, ScriptArgs.long(value, "long")).apply()
        }

        /**
         * The number stored under [key], with the precision of a 32-bit float: `0.1` reads back as
         * `0.10000000149011612`.
         *
         * @param key The key.
         * @param defaultValue What to return when [key] has no value. `0` when left out, or when it isn't a number.
         * @throws ClassCastException When the value wasn't stored by `putFloat`.
         */
        @JvmOverloads
        fun getFloat(key: Any?, defaultValue: Any? = 0f): Float =
            prefs.getFloat(keyOf(key), ScriptArgs.float(defaultValue, "defaultValue", 0f))

        /**
         * Stores the number [value], with the precision of a 32-bit float, under [key].
         *
         * @param key The key.
         * @param value A number, or a text of one.
         * @throws IllegalArgumentException `"float must be a number"` when [value] isn't a number.
         */
        fun putFloat(key: Any?, value: Any?) {
            val k = keyOf(key)
            prefs.edit().putFloat(k, ScriptArgs.float(value, "float")).apply()
        }

        /**
         * The boolean stored under [key].
         *
         * @param key The key.
         * @param defaultValue What to return when [key] has no value: a boolean, a number (`true` unless `0`), or the
         * text `"true"` or `"false"`. `false` when left out, or for anything else.
         * @throws ClassCastException When the value wasn't stored by `putBoolean`.
         * @throws IllegalArgumentException `"defaultValue must be a boolean"` when [defaultValue] is an object.
         */
        @JvmOverloads
        fun getBoolean(key: Any?, defaultValue: Any? = false): Boolean =
            prefs.getBoolean(keyOf(key), ScriptArgs.boolean(defaultValue, "defaultValue", false))

        /**
         * Stores the boolean [value] under [key].
         *
         * @param key The key.
         * @param value A boolean, a number (`true` unless `0`), or the text `"true"` or `"false"`. `false` for `null`,
         * `undefined` and any other text.
         * @throws IllegalArgumentException `"bool must be a boolean"` when [value] is an object.
         */
        fun putBoolean(key: Any?, value: Any?) {
            val k = keyOf(key)
            prefs.edit().putBoolean(k, ScriptArgs.boolean(value, "bool", false)).apply()
        }

        /**
         * The value that `putObject` stored under [key], as a script object, array or primitive.
         *
         * @param key The key.
         * @return `null` when [key] has no value, the text is empty or isn't valid JSON.
         * @throws ClassCastException When the value isn't text.
         */
        fun getObject(key: Any?): Any? {
            val text = prefs.getString(keyOf(key), "")
            if (text.isNullOrEmpty()) return null
            return try {
                json.parse(text)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Stores [value] under [key] as the text `JSON.stringify` gives, which `getObject` reads back.
         *
         * Nothing happens, and [key] isn't checked, when [value] has no JSON form, such as `undefined` or a function.
         * `getString` returns the JSON text.
         *
         * @param key The key.
         * @param value What `JSON.stringify` can write.
         * @throws Error An error from `JSON.stringify` (the engine's own error type) when [value] can't be written as
         * JSON, such as a cyclic structure or a BigInt.
         */
        fun putObject(key: Any?, value: Any?) {
            val text = json.stringify(value) ?: return
            putString(key, text)
        }

        /**
         * Stores every property of the object [values], each under its name, in one change.
         *
         * A value is stored as it is typed when it is a boolean (`putBoolean`), a whole number (`putInt`, or `putLong` if
         * it doesn't fit), or text (`putString`). Anything else, a fraction included, is stored as text (`1.5` as
         * `"1.5"`, an array as `[1, 2]`).
         *
         * @param values An object of keys and values.
         * @throws IllegalArgumentException `"data must be an object"` when [values] isn't an object.
         */
        fun putMultiple(values: Any?) {
            if (!ScriptValues.isObject(values))
                throw IllegalArgumentException("data must be an object")
            val entries = ScriptValues.keys(values).map { name -> name to ScriptValues.member(values, name) }
            val editor = prefs.edit()
            for ((name, value) in entries) {
                when {
                    value is Boolean -> editor.putBoolean(name, value)
                    value is Int || value is Short || value is Byte -> editor.putInt(name, (value as Number).toInt())
                    value is Long -> editor.putLong(name, value)
                    value is Float -> editor.putFloat(name, value)
                    value is Double && value.isFinite() && value == Math.floor(value) -> when {
                        value >= Int.MIN_VALUE && value <= Int.MAX_VALUE -> editor.putInt(name, value.toInt())
                        Math.abs(value) < LONG_LIMIT -> editor.putLong(name, value.toLong())
                        else -> editor.putString(name, ScriptArgs.stringify(value))
                    }
                    value is CharSequence -> editor.putString(name, value.toString())
                    else -> editor.putString(name, ScriptArgs.stringify(value))
                }
            }
            editor.apply()
        }

        /**
         * Whether [key] has a value.
         *
         * @param key The key.
         */
        fun contains(key: Any?): Boolean =
            prefs.contains(keyOf(key))

        /**
         * Removes the value of [key]. Nothing happens when it has none.
         *
         * @param key The key.
         */
        fun remove(key: Any?) {
            prefs.edit().remove(keyOf(key)).apply()
        }

        /**
         * Removes the values of every key of the array [keys], in one change.
         *
         * Every key is checked first: one that is `null` or `undefined` throws and removes nothing.
         *
         * @param keys An array of keys.
         * @throws IllegalArgumentException `"keys must be an array"` when [keys] isn't an array, or `"keys must be a
         * string"` for a key that is `null` or `undefined`.
         */
        fun removeMultiple(keys: Any?) {
            val items = ScriptValues.asList(keys) ?: throw IllegalArgumentException("keys must be an array")
            val names = items.map { ScriptArgs.string(it, "keys") }
            val editor = prefs.edit()
            names.forEach { editor.remove(it) }
            editor.apply()
        }

        /** Removes every key, of all projects. */
        fun clear() {
            prefs.edit().clear().apply()
        }

        /** How many keys there are. */
        fun size(): Int =
            prefs.all.size

        /** Whether there are no keys. */
        fun isEmpty(): Boolean =
            size() == 0

        /**
         * Every key, in no particular order, as an array of text.
         *
         * Unlike MessengerBot's, the array is a script array, so it has `map` and the like too.
         */
        fun getAllKeys(): Any? =
            json.parse(JsonArray(prefs.all.keys.map { JsonPrimitive(it) }).toString())

        /**
         * A copy of every key and value, as a `java.util.Map`: use `get(key)`, `size()` and `keySet()`, not properties.
         *
         * Values are `Boolean`, `Integer`, `Long`, `Float` or `String`, as they were stored. Changing the map doesn't
         * change the stored values.
         */
        fun getAll(): Map<String, *> =
            HashMap<String, Any?>(prefs.all)

        private companion object {
            const val PREFS_NAME = "script_app_data"
            const val LONG_LIMIT = 9.2e18
        }
    }

    override val name: String = "AppData"

    override val objects: List<ApiObject> = getApiObjects<AppData>()

    override val instanceClass: Class<AppData> = AppData::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = AppData(ScriptJson.current())
}
