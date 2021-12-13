package com.mooner.starlight.plugincore.utils

import java.io.File

operator fun File.plusAssign(file: File) {
    this.resolve(file)
}

operator fun File.plusAssign(directory: String) {
    this.resolve(directory)
}