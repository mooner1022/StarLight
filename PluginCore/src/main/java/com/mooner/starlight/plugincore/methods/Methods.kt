package com.mooner.starlight.plugincore.methods

import com.mooner.starlight.plugincore.Session
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
        fun getOriginalMethods(replier: Replier, logger: LocalLogger): Array<MethodBlock> {
            return arrayOf(
                MethodBlock(
                    "replier",
                    replier,
                    false,
                    listOf(
                        Method(
                            "reply",
                            arrayOf(String::class.java)
                        ),
                        Method(
                            "replyTo",
                            arrayOf(String::class.java, String::class.java)
                        )
                    )
                ),
                MethodBlock(
                    "logger",
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
                ),
                MethodBlock(
                    "Projects",
                    Projects,
                    true,
                    listOf(
                        Method(
                            "get",
                            arrayOf(String::class.java)
                        )
                    )
                ),
                MethodBlock(
                    "Languages",
                    Languages,
                    true,
                    listOf(
                        Method(
                            "get",
                            arrayOf(String::class.java)
                        )
                    )
                )
            )
        }

        fun getLegacyMethods(): Array<MethodBlock> {
            return arrayOf(
                MethodBlock(
                    "Api",
                    LegacyApi,
                    true,
                    listOf(
                        Method(
                            "gc",
                            arrayOf()
                        )
                    )
                ),
                MethodBlock(
                    "Utils",
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
                        Method(
                            "getAndroidVersionCode",
                            arrayOf()
                        ),
                        Method(
                            "getAndroidVersionName",
                            arrayOf()
                        ),
                        Method(
                            "getPhoneBrand",
                            arrayOf()
                        ),
                        Method(
                            "getPhoneModel",
                            arrayOf()
                        ),
                    )
                )
            )
        }
    }
}