package com.mooner.starlight.plugincore.method

import com.mooner.starlight.plugincore.project.Project

interface IMethod <T> {

    val name: String

    val type: MethodType

    val functions: List<MethodFunction>

    val instanceClass: Class<T>

    fun getInstance(project: Project): Any
}