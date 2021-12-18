package com.mooner.starlight.plugincore.utils

import java.io.File

fun File.hasFile(fileName: String): Boolean {
    return this.listFiles()?.find { it.name == fileName } != null
}

fun File.getFileSize(): Float {
    return this.length().toFloat() / (1024.0f * 1024.0f)
}

operator fun File.plusAssign(file: File) {
    this.resolve(file)
}

operator fun File.plusAssign(directory: String) {
    this.resolve(directory)
}