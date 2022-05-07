package dev.mooner.starlight.plugincore.config

import dev.mooner.starlight.plugincore.config.category.MutableConfigCategory

interface MutableConfig: Config {

    override operator fun get(id: String): MutableConfigCategory

    override fun category(id: String): MutableConfigCategory

    override fun categoryOrNull(id: String): MutableConfigCategory?

    fun push()

    fun edit(block: MutableConfig.() -> Unit)
}