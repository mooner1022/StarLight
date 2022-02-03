package dev.mooner.starlight.plugincore.utils

private const val digits = "0123456789ABCDEF"
fun bytesToHex(byteArray: ByteArray): String {
    val hexChars = CharArray(byteArray.size * 2)
    for (i in byteArray.indices) {
        val v = byteArray[i].toInt() and 0xff
        hexChars[i * 2] = digits[v shr 4]
        hexChars[i * 2 + 1] = digits[v and 0xf]
    }
    return String(hexChars)
}