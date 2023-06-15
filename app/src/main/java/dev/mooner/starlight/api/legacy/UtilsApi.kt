package dev.mooner.starlight.api.legacy

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Suppress("unused")
class UtilsApi: Api<UtilsApi.Utils>() {

    class Utils {

        companion object {
            @JvmStatic
            fun parse(url: String): Document {
                return Jsoup.connect(url).apply {
                    ignoreHttpErrors(true)
                    ignoreContentType(true)
                }.get()
            }

            @JvmStatic
            fun getWebText(url: String): String =
                parse(url).text()

            @JvmStatic
            fun getAndroidVersionCode(): Int =
                DeviceApi.Device.getAndroidVersionCode()

            @JvmStatic
            fun getAndroidVersionName(): String =
                DeviceApi.Device.getAndroidVersionName()

            @JvmStatic
            fun getPhoneBrand(): String =
                DeviceApi.Device.getPhoneBrand()

            @JvmStatic
            fun getPhoneModel(): String =
                DeviceApi.Device.getPhoneModel()

            /* NEW_API */

            private val alphanumericPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            @JvmStatic
            fun randomAlphanumeric(length: Int): String {
                val arr: ArrayList<Char> = arrayListOf()
                repeat(length) {
                    arr.add(alphanumericPool.random())
                }
                return arr.joinToString("")
            }

            @JvmStatic
            val lw: String by lazy { "\u200b".repeat(500) }

            @JvmStatic
            val lwLined: String = lw + "\n" + "─".repeat(20) + "\n"
        }
    }

    override val name: String = "Utils"

    override val instanceType: InstanceType = InstanceType.CLASS

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

    override fun getInstance(project: Project): Any = Utils::class.java
}