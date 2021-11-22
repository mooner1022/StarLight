package com.mooner.starlight.api.helper

import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiObject
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.Project
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class UtilsApi: Api<UtilsApi.Utils>() {

    object Utils {

        fun parse(url: String): Document {
            return Jsoup.connect(url).apply {
                ignoreHttpErrors(true)
                ignoreContentType(true)
            }.get()
        }

        fun getWebText(url: String): String = parse(url).text()

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

        val lw: String by lazy { "\u200b".repeat(500) }

        val lwLined: String = lw + "\n" + "─".repeat(20) + "\n"
    }

    override val name: String = "Utils"

    override val instanceType: InstanceType = InstanceType.OBJECT

    override val instanceClass: Class<Utils> = Utils::class.java

    override val objects: List<ApiObject> = listOf(
        function {
            name = "getWebText"
            args = arrayOf(String::class.java)
            returns = String::class.java
        },
        function {
            name = "parse"
            args = arrayOf(String::class.java)
            returns = Document::class.java
        },
        function {
            name = "getAndroidVersionCode"
            returns = Int::class.java
        },
        function {
            name = "getAndroidVersionName"
            returns = String::class.java
        },
        function {
            name = "getPhoneBrand"
            returns = String::class.java
        },
        function {
            name = "getPhoneModel"
            returns = String::class.java
        },
        function {
            name = "randomAlphanumeric"
            args = arrayOf(Int::class.java)
            returns = String::class.java
        },
        value {
            name = "lw"
            returns = String::class.java
        },
        value {
            name = "lwLined"
            returns = String::class.java
        }
    )

    override fun getInstance(project: Project): Any = Utils
}