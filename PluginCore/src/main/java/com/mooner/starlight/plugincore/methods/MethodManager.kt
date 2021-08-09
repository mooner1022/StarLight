package com.mooner.starlight.plugincore.methods

import com.mooner.starlight.plugincore.language.Method
import com.mooner.starlight.plugincore.language.MethodBlock
import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.methods.legacy.Utils
import com.mooner.starlight.plugincore.methods.original.Languages
import com.mooner.starlight.plugincore.methods.original.Projects

object MethodManager {

    private val methods: HashSet<MethodBlock> = hashSetOf()

    private val loggerMethod = MethodBlock(
        "Logger",
        LocalLogger::class.java,
        Logger,
        listOf(
            Method(
                "i",
                arrayOf(String::class.java, String::class.java)
            ),
            Method(
                "e",
                arrayOf(String::class.java, String::class.java)
            ),
            Method(
                "w",
                arrayOf(String::class.java, String::class.java)
            ),
            Method(
                "d",
                arrayOf(String::class.java, String::class.java)
            ),
        )
    )

    private val projectsMethod = MethodBlock(
        "Projects",
        Projects::class.java,
        Projects,
        listOf(
            Method(
                "get",
                arrayOf(String::class.java)
            )
        )
    )

    private val languagesMethod = MethodBlock(
        "Languages",
        Languages::class.java,
        Languages,
        listOf(
            Method(
                "get",
                arrayOf(String::class.java)
            )
        )
    )

    private val utilsMethod = MethodBlock(
        "Utils",
        Utils::class.java,
        Utils,
        listOf(
            Method(
                "getWebText",
                arrayOf(String::class.java)
            ),
            Method(
                "parse",
                arrayOf(String::class.java)
            ),
            Method("getAndroidVersionCode"),
            Method("getAndroidVersionName"),
            Method("getPhoneBrand"),
            Method("getPhoneModel")
        )
    )

    init {
        addMethod(loggerMethod, projectsMethod, languagesMethod, utilsMethod)
    }

    fun addMethod(vararg methodBlocks: MethodBlock) {
        for (block in methodBlocks) {
            if (block in methods) return
            methods.add(block)
        }
    }

    fun getMethods(): List<MethodBlock> = methods.toList()
}