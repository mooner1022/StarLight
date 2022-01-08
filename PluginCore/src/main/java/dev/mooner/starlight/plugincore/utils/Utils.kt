package dev.mooner.starlight.plugincore.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.stream.Collectors

fun InputStream.readString(): String {
    return BufferedReader(InputStreamReader(this))
        .lines()
        .parallel()
        .collect(Collectors.joining("\n"))
}

fun isNightMode(context: Context): Boolean {
    return when (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true
        Configuration.UI_MODE_NIGHT_NO -> false
        Configuration.UI_MODE_NIGHT_UNDEFINED -> false
        else -> false
    }
}

inline fun color(color: () -> String): Int = Color.parseColor(color())

fun Bitmap.toBase64(): String {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, stream)
    val bitmapByte = stream.toByteArray()
    return Base64.encodeToString(bitmapByte, Base64.DEFAULT)
}

fun requiredField(fieldName: String, value: Any?) {
    if (value == null) {
        throw IllegalArgumentException("Required field '$fieldName' is null")
    }
}