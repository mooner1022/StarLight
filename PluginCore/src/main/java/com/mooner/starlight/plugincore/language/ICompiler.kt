package com.mooner.starlight.plugincore.language

interface ICompiler {
    val instanceOf: String

    fun onCompile(from: String): String
}