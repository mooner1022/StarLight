package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import com.mooner.starlight.plugincore.method.MethodType
import com.mooner.starlight.plugincore.project.Project

class LanguagesMethod: Method<LanguagesMethod.Languages>() {

    class Languages(
        private val project: Project
    ) {
        fun get(): Language = project.getLanguage() as Language

        fun get(id: String): Language? {
            return Session.getLanguageManager().getLanguage(id)
        }
    }

    override val name: String = "Languages"

    override val type: MethodType = MethodType.OBJECT

    override val instanceClass: Class<Languages> = Languages::class.java

    override val functions: List<MethodFunction> = listOf(
        MethodFunction(
            name = "get",
            args = arrayOf(String::class.java)
        )
    )

    override fun getInstance(project: Project): Any = Languages(project)
}