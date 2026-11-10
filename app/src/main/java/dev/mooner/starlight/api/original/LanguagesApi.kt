/*
 * LanguageManagerApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiFunction
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.project.Project

@Suppress("unused")
class LanguagesApi: Api<LanguagesApi.Languages>() {

    /**
     * The script languages StarLight runs: this project's own, or any installed one by its ID.
     *
     * A language is a `Language` (`describe_api Language`); scripts usually read its `id` and `name`.
     */
    class Languages(
        private val project: Project
    ) {
        /**
         * The language this project is written in.
         *
         * Its `id` is `js_neon` for NeonJS, `JS_RHINO` for Rhino, or the ID of a plugin's language.
         */
        fun getSelf(): Language =
            project.getLanguage()

        /**
         * The installed language with the ID [id], or `null` if there is none.
         *
         * @param id The language's ID, compared exactly: `js_neon`, `JS_RHINO`, or the ID of a plugin's language.
         */
        fun ofId(id: String): Language? =
            Session.languageManager.getLanguage(id)
    }

    override val name: String = "Languages"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<Languages> = Languages::class.java

    override val objects: List<ApiFunction> = listOf(
        function {
            name = "getSelf"
            returns = Language::class.java
        },
        function {
            name = "ofId"
            args = arrayOf(String::class.java)
            returns = Language::class.java
        }
    )

    override fun getInstance(project: Project): Any =
        Languages(project)
}