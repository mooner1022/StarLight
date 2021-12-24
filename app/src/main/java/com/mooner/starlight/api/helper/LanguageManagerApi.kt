package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.project.Project

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