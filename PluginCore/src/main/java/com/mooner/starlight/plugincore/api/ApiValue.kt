package com.mooner.starlight.plugincore.api

data class ApiValue(
    override val name: String,
    override val returns: Class<*>
): ApiObject