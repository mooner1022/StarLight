package dev.mooner.starlight.plugincore.utils

import android.os.Environment
import java.io.File
import java.io.IOException

@Suppress("DEPRECATION")
fun getStarLightDirectory() =
    File(Environment.getExternalStorageDirectory(), "StarLight/")

fun File.copyAsReadOnly(directory: File): File {
    val target = directory.resolve("${length()}-${lastModified()}.$extension")
    if (!target.exists()) {
        directory.deleteRecursively()
        directory.mkdirs()
        val temp = directory.resolve("${target.name}.tmp")
        copyTo(temp)
        if (!temp.renameTo(target))
            throw IOException("Failed to move $temp to $target")
    }
    target.setReadOnly()
    return target
}

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