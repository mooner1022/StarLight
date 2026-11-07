package dev.mooner.starlight.api.legacy

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Suppress("unused")
class UtilsApi: Api<UtilsApi.Utils>() {

    /**
     * The `Utils` global of legacy projects: MessengerBot's helpers for web pages, device facts and long messages.
     *
     * Its members are static: use them on `Utils` itself.
     *
     * Of MessengerBot's `Utils`, only these exist. `compress`, `delay`, `sleep`, `removeTags`, `xmlToJson`,
     * `getWebText2` and its other helpers are missing, and so is `getWebText(url, removeTags)`.
     *
     * @see guide:projects#api-levels
     */
    class Utils {

        companion object {
            /**
             * Downloads the page at [url] and parses it with jsoup.
             *
             * The request is a GET, and the script waits until the page has arrived. An HTTP error status doesn't
             * throw: the error page is parsed. Any content type is parsed as HTML; `text()` of the result gives a
             * JSON body back, with runs of whitespace collapsed.
             *
             * @param url An absolute `http://` or `https://` URL.
             * @return The parsed page, an `org.jsoup.nodes.Document`.
             * @throws java.io.IOException When the request fails, such as on a network error or a timeout.
             * @throws IllegalArgumentException When [url] is malformed.
             */
            @JvmStatic
            fun parse(url: String): Document {
                return Jsoup.connect(url).apply {
                    ignoreHttpErrors(true)
                    ignoreContentType(true)
                }.get()
            }

            /**
             * The visible text of the page at [url], with its HTML tags removed.
             *
             * It is `Utils.parse(url).text()`: runs of whitespace are collapsed, so line breaks are lost. This differs
             * from MessengerBot, whose `getWebText(url)` returns the page's HTML; for that, use
             * `Utils.parse(url).html()`.
             *
             * @param url An absolute `http://` or `https://` URL.
             * @throws java.io.IOException When the request fails, such as on a network error or a timeout.
             * @throws IllegalArgumentException When [url] is malformed.
             */
            @JvmStatic
            fun getWebText(url: String): String =
                parse(url).text()

            /**
             * The device's Android API level, such as `34` for Android 14.
             */
            @JvmStatic
            fun getAndroidVersionCode(): Int =
                DeviceApi.Device.getAndroidVersionCode()

            /**
             * The device's Android version as users see it, such as `"14"`.
             */
            @JvmStatic
            fun getAndroidVersionName(): String =
                DeviceApi.Device.getAndroidVersionName()

            /**
             * The device's product name, `android.os.Build.PRODUCT`.
             *
             * Despite its name, it isn't the brand that MessengerBot returns: `android.os.Build.BRAND` holds that.
             */
            @JvmStatic
            fun getPhoneBrand(): String =
                DeviceApi.Device.getPhoneBrand()

            /**
             * The device's model name, `android.os.Build.MODEL`.
             */
            @JvmStatic
            fun getPhoneModel(): String =
                DeviceApi.Device.getPhoneModel()

            /* NEW_API */

            private val alphanumericPool: List<Char> = listOf('a'..'z', 'A'..'Z', '0'..'9').flatten()
            /**
             * A random string of [length] letters and digits.
             *
             * Each character is drawn from `a`–`z`, `A`–`Z` and `0`–`9`. The generator isn't cryptographically secure,
             * so don't use it for secrets.
             *
             * @param length The number of characters; `0` or less gives an empty string.
             */
            @JvmStatic
            fun randomAlphanumeric(length: Int): String {
                val arr: ArrayList<Char> = arrayListOf()
                repeat(length) {
                    arr.add(alphanumericPool.random())
                }
                return arr.joinToString("")
            }

            /**
             * A string of 500 zero-width spaces (`\u200b`).
             *
             * Put in a message, it folds the text after it behind KakaoTalk's 전체보기 (see more).
             */
            @JvmStatic
            val lw: String by lazy { "\u200b".repeat(500) }

            /**
             * [lw] followed by a line break, a rule of 20 `─` and another line break.
             */
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