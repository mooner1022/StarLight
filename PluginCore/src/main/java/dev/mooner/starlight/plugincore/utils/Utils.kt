package dev.mooner.starlight.plugincore.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import dev.mooner.configdsl.LazyMessage
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

private val colorCache: MutableMap<Int, Int> = hashMapOf()
fun color(color: () -> String): Int {
    val nColor = color()
    val nHash = nColor.hashCode()
    return colorCache[nHash]
        ?: Color.parseColor(nColor)
            .also { colorCache[nHash] = it }
}

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

fun <A, B> pairOf(first: A, second: B): Pair<A, B> = Pair(first, second)

fun Boolean.runIf(value: Boolean, block: () -> Unit) {
    if (this == value) block()
}

inline fun <T> T.require(condition: T.() -> Boolean, lazyMessage: LazyMessage): T =
    if (this.condition()) this else throw IllegalArgumentException(lazyMessage())