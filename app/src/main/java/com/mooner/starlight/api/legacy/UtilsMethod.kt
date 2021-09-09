package com.mooner.starlight.api.legacy

import com.mooner.starlight.plugincore.method.Method
import com.mooner.starlight.plugincore.method.MethodFunction
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class UtilsMethod: Method() {

    fun getWebText(url: String): String {
        return Jsoup.connect(url).apply {
            ignoreHttpErrors(true)
            ignoreContentType(true)
        }.get().text()
    }

    fun parse(url: String): Document {
        return Jsoup.connect(url).apply {
            ignoreHttpErrors(true)
            ignoreContentType(true)
        }.get()
    }

    fun getAndroidVersionCode(): Int {
        return android.os.Build.VERSION.SDK_INT
    }

    fun getAndroidVersionName(): String {
        return android.os.Build.VERSION.RELEASE
    }

    fun getPhoneBrand(): String {
        return android.os.Build.PRODUCT
    }

    fun getPhoneModel(): String {
        return android.os.Build.MODEL
    }

    /* NEW_API */

    private val alphanumericPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    fun randomAlphanumeric(length: Int): String {
        val arr: ArrayList<Char> = arrayListOf()
        repeat(length) {
            arr.add(alphanumericPool.random())
        }
        return arr.joinToString("")
    }

    override val name: String = "Utils"

    override val instance: Any
        get() = UtilsMethod()

    override val functions: List<MethodFunction> = listOf(
        MethodFunction(
            name = "getWebText",
            args = arrayOf(String::class.java)
        ),
        MethodFunction(
            name = "parse",
            args = arrayOf(String::class.java)
        ),
        MethodFunction(name = "getAndroidVersionCode"),
        MethodFunction(name = "getAndroidVersionName"),
        MethodFunction(name = "getPhoneBrand"),
        MethodFunction(name = "getPhoneModel"),
        MethodFunction(
            name = "randomAlphanumeric",
            args = arrayOf(Int::class.java)
        )
    )
}