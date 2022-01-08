package dev.mooner.starlight.utils

import android.os.Environment
import java.io.File

class FileUtils {
    companion object {
        @Suppress("DEPRECATION")
        fun getInternalDirectory() = File(Environment.getExternalStorageDirectory(), "StarLight/")
    }
}