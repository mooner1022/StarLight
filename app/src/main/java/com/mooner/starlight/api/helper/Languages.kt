package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction

class Languages: Method() {
    companion object {
        fun get(id: String): Language? {
            return Session.getLanguageManager().getLanguage(id)
        }
    }

    override val name: String = "Languages"

    override val instance: Any = this

    override val functions: List<MethodFunction> = listOf(
        MethodFunction(
            name = "get",
            args = arrayOf(String::class.java)
        )
    )
}