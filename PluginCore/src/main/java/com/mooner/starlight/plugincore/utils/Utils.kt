package com.mooner.starlight.plugincore.utils

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

        fun File.getFileSize(): Float {
            return this.length().toFloat() / (1024.0f * 1024.0f)
        }
    }
}