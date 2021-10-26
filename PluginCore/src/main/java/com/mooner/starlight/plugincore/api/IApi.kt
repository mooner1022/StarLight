package com.mooner.starlight.plugincore.api

import com.mooner.starlight.plugincore.project.Project

interface IApi <T> {

    val name: String

    val functions: List<ApiFunction>

    val instanceClass: Class<T>

    val instanceType: InstanceType

    fun getInstance(project: Project): Any
}