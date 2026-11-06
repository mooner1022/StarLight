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

    class Languages(
        private val project: Project
    ) {
        fun getSelf(): Language =
            project.getLanguage()

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