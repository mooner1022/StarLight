package com.mooner.starlight.plugincore.methods

import com.mooner.starlight.plugincore.language.Method
import com.mooner.starlight.plugincore.language.MethodBlock
import com.mooner.starlight.plugincore.logger.LocalLogger
import com.mooner.starlight.plugincore.methods.legacy.LegacyApi
import com.mooner.starlight.plugincore.methods.legacy.Utils
import com.mooner.starlight.plugincore.methods.original.Languages
import com.mooner.starlight.plugincore.methods.original.Projects
import com.mooner.starlight.plugincore.project.Replier

class Methods {
    companion object {
        fun getLogger(logger: LocalLogger): Array<MethodBlock> {
            return arrayOf(
                    MethodBlock(
                            "Logger",
                            LocalLogger::class.java,
                            logger,
                            false,
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
            )
        }

        private val replier = MethodBlock(
            "Replier",
            Replier::class.java,
            0,
            true,
            listOf(
                Method(
                    "reply",
                    arrayOf(String::class.java)
                ),
                Method(
                    "reply",
                    arrayOf(String::class.java, String::class.java)
                )
            )
        )

        private val projects = MethodBlock(
                "Projects",
                Projects::class.java,
                Projects,
                true,
                listOf(
                        Method(
                                "get",
                                arrayOf(String::class.java)
                        )
                )
        )

        private val languages = MethodBlock(
                "Languages",
                Languages::class.java,
                Languages,
                true,
                listOf(
                        Method(
                                "get",
                                arrayOf(String::class.java)
                        )
                )
        )

        /*
        private val timer = MethodBlock(
                "Timer",
                Timer::class.java,
                Timer,
                true,
                listOf(
                        Method(
                                "schedule",
                                arrayOf(Long::class.java, Function::class.java)
                        )
                )
        )
        */

        private val utils = MethodBlock(
                "Utils",
                Utils::class.java,
                Utils,
                true,
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

        fun getApi(vararg index: Int): Array<MethodBlock> {
            val rarr: ArrayList<MethodBlock> = arrayListOf()
            for (i in index) {
                rarr += when(i) {
                    1 -> arrayOf(
                            MethodBlock(
                                    "Api",
                                    LegacyApi::class.java,
                                    LegacyApi,
                                    true,
                                    listOf(
                                            Method(
                                                    "gc",
                                                    arrayOf()
                                            )
                                    )
                            ),
                            utils
                    )
                    2 -> arrayOf()
                    3 -> arrayOf()
                    4 -> arrayOf(languages, projects)
                    else -> arrayOf()
                }
            }
            return rarr.toTypedArray()
        }
    }
}