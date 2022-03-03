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

class LanguageManagerApi: Api<LanguageManagerApi.LanguageManager>() {

    class LanguageManager(
        private val project: Project
    ) {
        fun getLanguage(): Language = project.getLanguage()

        fun getLanguage(id: String): Language? {
            return Session.languageManager.getLanguage(id)
        }
    }

    override val name: String = "LanguageManager"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<LanguageManager> = LanguageManager::class.java

    override val objects: List<ApiFunction> = listOf(
        function {
            name = "getLanguage"
            returns = Language::class.java
        },
        function {
            name = "getLanguage"
            args = arrayOf(String::class.java)
            returns = Language::class.java
        }
    )

    override fun getInstance(project: Project): Any = LanguageManager(project)
}