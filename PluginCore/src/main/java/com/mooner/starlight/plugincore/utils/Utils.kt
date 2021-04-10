package com.mooner.starlight.plugincore.utils

import com.mooner.starlight.plugincore.Session.Companion.getLogger
import com.mooner.starlight.plugincore.language.Method
import com.mooner.starlight.plugincore.language.MethodBlock
import java.io.BufferedReader
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

        fun getDefaultMethods(replier: Any): Array<MethodBlock> {
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
                            getLogger(),
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
                                            "f",
                                            arrayOf(String::class.java, String::class.java)
                                    ),
                            )
                    )
            )
        }
    }
}