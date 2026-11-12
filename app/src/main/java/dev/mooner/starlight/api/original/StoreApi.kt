/*
 * StoreApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.script.store.StoreData
import dev.mooner.starlight.script.store.Stores
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

class StoreApi: Api<StoreApi.Store>() {

    /**
     * Values the project keeps across compiles and restarts, saved as JSON files; a StarLight original global.
     *
     * `Store.open(name)` gives a named set of values, kept in `data/store/<name>.json` in the project's folder:
     *
     * ```js
     * const scores = Store.open("scores");
     * export function onMessage(event) {
     *     if (event.message !== "!point") return;
     *     const total = scores.update(event.sender.name, (n) => (n ?? 0) + 1);
     *     event.room.send(`${event.sender.name}: ${total}`);
     * }
     * ```
     *
     * A value is what `JSON.stringify` can write: objects, arrays, strings, numbers, booleans and `null`. It is kept as
     * a copy, so `get` gives a new object each time, and a changed object is kept only when it is `set` again. A
     * `Date` is kept as its ISO text; a Java object, such as `event.sender`, is not kept as one: keep the fields you
     * need. NeonJS can't write a BigInt as JSON: keep 64-bit IDs as text (`String(id)`).
     *
     * Changes are saved in the background within a second, when the project compiles again or is turned off, and at
     * once with `save()`. A store suits settings, counters and per-user values up to a few megabytes: every save writes
     * its whole file. Each project has its own stores; another project's aren't reachable.
     *
     * The file may be edited while the project runs, by hand or by StarLight AI: the edit is read when the script
     * opens the store again, as after a compile, and a save keeps it under the script's own changes, unless both
     * changed the same key.
     *
     * @see guide:projects#storing-data
     */
    class Store internal constructor(
        private val json: ScriptJson,
        private val dataOf: (name: String) -> StoreData,
    ) {

        private val opened: MutableMap<String, StoreFile> = ConcurrentHashMap()

        /**
         * The store [name], read from `data/store/<name>.json` in the project's folder; an empty one when the file
         * doesn't exist yet.
         *
         * Opening the same name again gives the same store. The file is read again when it changed since, as after
         * an edit by hand.
         *
         * @param name Up to 64 letters, digits, `_`, `-` or `.`, not starting with `.`.
         * @throws IllegalArgumentException When [name] can't be a store's name.
         * @throws IllegalStateException When the file holds no JSON object; it is left as it is, for fixing.
         */
        fun open(name: String): StoreFile {
            Stores.checkName(name)
            val data = dataOf(name)
            data.open()
            return opened.getOrPut(name) { StoreFile(data, json) }
        }
    }

    /**
     * A named set of values from `Store.open(name)`.
     *
     * Every value is a copy, as JSON keeps it. Several events may use the store at once in Rhino, whose projects run up
     * to three events together: change a value from its old one with `update`, which no other change can come
     * between.
     */
    class StoreFile internal constructor(
        private val data: StoreData,
        private val json: ScriptJson,
    ) {

        /** The store's name, as `Store.open` was given it. */
        val name: String
            get() = data.name

        /** How many keys the store has. */
        val size: Int
            get() = data.size()

        /**
         * A copy of the value of [key]; `null` when the store has none.
         *
         * @param key The value's key.
         */
        fun get(key: String): Any? =
            data.get(key)?.let(::toScript)

        /**
         * A copy of the value of [key]; [defaultValue], as it was given, when the store has none.
         *
         * A value kept as `null` is returned as `null`, not as [defaultValue].
         *
         * @param key The value's key.
         * @param defaultValue What to return when [key] has no value.
         */
        fun get(key: String, defaultValue: Any?): Any? =
            data.get(key)?.let(::toScript) ?: defaultValue.takeUnless { data.has(key) }

        /** A copy of every value, as an object of keys and values. */
        fun getAll(): Any? =
            toScript(data.all())

        /**
         * Keeps a copy of [value] under [key], replacing the one it had.
         *
         * `undefined` can't be kept: NeonJS passes it as `null`, and Rhino as the text `"undefined"`. To remove a value,
         * use `delete`.
         *
         * @param key The value's key.
         * @param value What `JSON.stringify` can write.
         * @return The store, for setting more.
         * @throws IllegalArgumentException When [value] has no JSON form, as a function.
         */
        fun set(key: String, value: Any?): StoreFile {
            data.set(key, toJson(value))
            return this
        }

        /**
         * Replaces the value of [key] with what [transform] returns for a copy of the old one, with no other change of
         * the store in between, and returns the new value.
         *
         * Other events using the store wait while [transform] runs, so keep it short, and don't use the store from inside
         * it.
         *
         * ```js
         * const visits = counters.update("visits", (n) => (n ?? 0) + 1);
         * ```
         *
         * @param key The value's key.
         * @param transform Given a copy of the old value, `null` when there is none, and returns the new one.
         * @return What [transform] returned.
         * @throws IllegalArgumentException When the new value has no JSON form, as a function.
         */
        fun update(key: String, transform: (old: Any?) -> Any?): Any? {
            var result: Any? = null
            data.update(key) { old ->
                result = transform(old?.let(::toScript))
                toJson(result)
            }
            return result
        }

        /**
         * Whether the store has a value for [key], `null` included.
         *
         * @param key The value's key.
         */
        fun has(key: String): Boolean =
            data.has(key)

        /**
         * Removes the value of [key].
         *
         * @param key The value's key.
         * @return Whether there was one.
         */
        fun delete(key: String): Boolean =
            data.delete(key)

        /** The store's keys, in the order they were first set, as an array. */
        fun keys(): Any? =
            toScript(JsonArray(data.keys().map(::JsonPrimitive)))

        /** Removes every value of the store. */
        fun clear() =
            data.clear()

        /**
         * Saves the store's changes now, rather than within a second.
         *
         * @throws java.io.IOException When the file can't be written.
         */
        fun save() =
            data.save()

        override fun toString(): String =
            "Store(${data.name})"

        private fun toScript(element: JsonElement): Any? =
            json.parse(element.toString())

        private fun toJson(value: Any?): JsonElement {
            val text = json.stringify(value)
                ?: throw IllegalArgumentException("${describe(value)} can't be kept: it has no JSON form")
            return Json.parseToJsonElement(text)
        }

        private fun describe(value: Any?): String =
            if (value == null) "undefined" else "This value"
    }

    override val name: String = "Store"

    override val objects: List<ApiObject> =
        getApiObjects<Store>()

    override val instanceClass: Class<Store> =
        Store::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any {
        Stores.watch(project)
        return Store(ScriptJson.current()) { name -> Stores.of(project, name) }
    }
}
