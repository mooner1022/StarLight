package com.mooner.starlight.plugincore.method

interface IMethod {

    val name: String

    val instance: Any

    val functions: List<MethodFunction>
}