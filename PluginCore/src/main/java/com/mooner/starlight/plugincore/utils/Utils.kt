package com.mooner.starlight.plugincore.utils

import com.mooner.starlight.plugincore.Session.Companion.getLogger
import com.mooner.starlight.plugincore.language.Method
import com.mooner.starlight.plugincore.language.MethodBlock
import com.mooner.starlight.plugincore.logger.LocalLogger
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.stream.Collectors

class Utils {
    companion object {
        fun InputStream.readString(): String {
            return BufferedReader(InputStreamReader(this))
                .lines()
                .parallel()
                .collect(Collectors.joining("\n"))
        }

        fun File.hasFile(fileName: String): Boolean {
            return this.listFiles()?.find { it.name == fileName } != null
        }

        fun getDefaultMethods(replier: Any, logger: LocalLogger): Array<MethodBlock> {
            return arrayOf(
                    MethodBlock(
                            "replier",
                            replier,
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
    }
}