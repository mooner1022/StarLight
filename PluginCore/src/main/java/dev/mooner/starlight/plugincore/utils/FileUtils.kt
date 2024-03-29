package dev.mooner.starlight.plugincore.utils

import android.os.Environment
import java.io.File

@Suppress("DEPRECATION")
fun getStarLightDirectory() =
    File(Environment.getExternalStorageDirectory(), "StarLight/")

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

val File?.isValidFile
    get() = this != null && this.exists() && this.isFile

val File?.isValidDirectory
    get() = this != null && this.exists() && this.isDirectory