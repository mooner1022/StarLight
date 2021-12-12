package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiFunction
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.language.ILanguage
import com.mooner.starlight.plugincore.project.Project

class LanguagesApi: Api<LanguagesApi.Languages>() {

    class Languages(
        private val project: Project
    ) {
        fun get(): ILanguage = project.getLanguage()

        fun get(id: String): ILanguage? {
            return Session.languageManager.getLanguage(id)
        }
    }

    override val name: String = "Languages"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<Languages> = Languages::class.java

    override val objects: List<ApiFunction> = listOf(
        ApiFunction(
            name = "get",
            args = arrayOf(String::class.java)
        )
    )

    override fun getInstance(project: Project): Any = Languages(project)
}