package com.mooner.starlight.plugincore.method

import com.mooner.starlight.plugincore.project.Project

interface IMethod {

    val name: String

    val functions: List<MethodFunction>

    fun getInstance(project: Project): Any
}