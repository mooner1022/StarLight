package com.mooner.starlight.plugincore.config

interface Config {
    operator fun get(id: String): ConfigCategory

    fun getCategory(id: String): ConfigCategory

    fun getCategoryOrNull(id: String): ConfigCategory?
}