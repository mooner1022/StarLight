package dev.mooner.starlight.api.legacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.WebSettings
import android.widget.Toast
import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.languages.neonjs.whileWaitingForAnotherThread
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

@Suppress("unused")
class UtilsApi: Api<UtilsApi.Utils>() {

    /**
     * The `Utils` global of legacy projects: MessengerBot's helpers for web pages, text, timing and device facts.
     *
     * Its members are static: use them on `Utils` itself. Of MessengerBot's `Utils`, everything exists except
     * `getWeatherJSON`. The long-message texts are also values: `Utils.lw` and `Utils.lwLined` read the same as
     * `Utils.getLw()` and `Utils.getLwLined()`.
     *
     * @see guide:projects#api-levels
     */
    class Utils {

        companion object {

            private const val TIMEOUT_MILLIS = 10_000

            private const val REFERRER = "http://www.google.com"

            private val userAgent: String by lazy {
                val browser = try {
                    WebSettings.getDefaultUserAgent(GlobalApplication.requireContext())
                } catch (_: Throwable) {
                    "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                "$browser StarLight"
            }

            private fun connect(url: String) =
                Jsoup.connect(url)
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MILLIS)
                    .userAgent(userAgent)
                    .referrer(REFERRER)

            /**
             * Downloads the page at [url] and parses it with jsoup.
             *
             * The request is a GET with a browser-like user agent, and the script waits until the page has arrived,
             * for 10 seconds at most. An HTTP error status throws. Any content type is parsed as HTML; `text()` of
             * the result gives a JSON body back, with runs of whitespace collapsed.
             *
             * @param url An absolute `http://` or `https://` URL.
             * @return The parsed page, an `org.jsoup.nodes.Document`.
             * @throws IllegalArgumentException When [url] isn't a string.
             * @throws RuntimeException `Failed to parse URL: <url>` when the request fails, such as on a network
             * error, a timeout or an HTTP error status, or the URL is malformed. The cause holds the details.
             */
            @JvmStatic
            fun parse(url: Any?): Document {
                val address = ScriptArgs.string(url, "url")
                try {
                    return whileWaitingForAnotherThread { connect(address).get() }
                } catch (e: Exception) {
                    throw RuntimeException("Failed to parse URL: $address", e)
                }
            }

            /**
             * The HTML of the page at [url], as jsoup writes the parsed page.
             *
             * It is `Utils.parse(url).toString()`: the whole page's HTML, normalized, not its text; use
             * `Utils.removeTags(html)` or `Utils.parse(url).text()` for that. A JSON body comes back wrapped in
             * `<html><head></head><body>...</body></html>`.
             *
             * @param url An absolute `http://` or `https://` URL.
             * @return The HTML, or `null` when anything goes wrong: a network error, a timeout, an HTTP error status
             * or a malformed URL.
             * @throws IllegalArgumentException When [url] isn't a string.
             */
            @JvmStatic
            fun getWebText(url: Any?): String? {
                val address = ScriptArgs.string(url, "str")
                return try {
                    whileWaitingForAnotherThread { connect(address).get() }.toString()
                } catch (_: Exception) {
                    null
                }
            }

            /**
             * The device's Android API level, such as `34` for Android 14.
             */
            @JvmStatic
            fun getAndroidVersionCode(): Int =
                Build.VERSION.SDK_INT

            /**
             * The device's Android version as users see it, such as `"14"`.
             */
            @JvmStatic
            fun getAndroidVersionName(): String =
                Build.VERSION.RELEASE

            /**
             * The device's brand, `android.os.Build.BRAND`, such as `"samsung"`.
             */
            @JvmStatic
            fun getPhoneBrand(): String =
                Build.BRAND

            /**
             * The device's model name, `android.os.Build.MODEL`.
             */
            @JvmStatic
            fun getPhoneModel(): String =
                Build.MODEL

            /**
             * A string of 1000 zero-width spaces followed by a line break.
             *
             * Put in front of a long message, it folds the text behind KakaoTalk's 전체보기 (see more) button.
             */
            @JvmStatic
            fun compress(): String =
                "​".repeat(1000) + "\n"

            /**
             * Puts [value] on the clipboard as plain text.
             *
             * `null` and `undefined` are put as `null` on both engines (Rhino's `undefined` is recognised by
             * [ScriptArgs.isUndefined]); other values as their text.
             *
             * @param value The text to copy.
             * @return `true`.
             */
            @JvmStatic
            fun copyToClipboard(value: Any?): Boolean {
                val text = ScriptArgs.stringify(value)
                val manager = GlobalApplication.requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("label", text))
                return true
            }

            /**
             * Waits for [mil] milliseconds, blocking the script.
             *
             * A missing or non-numeric value waits `0`. Same as [sleep].
             *
             * @param mil The time to wait, in milliseconds.
             * @throws IllegalArgumentException When [mil] is negative.
             */
            @JvmStatic
            fun delay(mil: Any?) {
                pause(ScriptArgs.long(mil, "mil", 0L))
            }

            /**
             * Waits for [millis] milliseconds, blocking the script.
             *
             * A missing or non-numeric value waits `0`. Same as [delay].
             *
             * @param millis The time to wait, in milliseconds.
             * @throws IllegalArgumentException When [millis] is negative.
             */
            @JvmStatic
            fun sleep(millis: Any?) {
                pause(ScriptArgs.long(millis, "millis", 0L))
            }

            private fun pause(millis: Long) {
                try {
                    whileWaitingForAnotherThread { Thread.sleep(millis) }
                } catch (_: InterruptedException) {
                    // Cut short, as when the project is turned off.
                }
            }

            /**
             * A random string of [length] letters and digits.
             *
             * Each character is drawn from `A`-`Z`, `a`-`z` and `0`-`9`. The generator isn't cryptographically secure,
             * so don't use it for secrets.
             *
             * @param length The number of characters, as a number or numeric text; a fraction is cut off, and `0` or
             * less gives an empty string.
             * @throws IllegalArgumentException `length must be a number` when it isn't one.
             */
            @JvmStatic
            fun randomAlphanumeric(length: Any?): String =
                UtilsSupport.randomAlphanumeric(ScriptArgs.int(length, "length"))

            /**
             * A random user agent of a desktop browser: Chrome, Firefox, Internet Explorer, Safari or Opera, the
             * first two most often.
             *
             * @return A user agent string, usable with `Jsoup.connect(url).userAgent(...)`.
             */
            @JvmStatic
            fun getRandomUserAgent(): String =
                UtilsSupport.randomUserAgent()

            /**
             * Whether [value] is `undefined`.
             *
             * `null`, `0`, empty text and objects are not. Rhino's `undefined` reaches the method as the JVM's one
             * shared instance of the text `"undefined"`, which is told apart by identity: a string a script builds stays
             * text, but text Rhino itself made, as what `typeof` gives, counts as `undefined`. NeonJS passes
             * `undefined` and `null` alike as `null`, so there an `undefined` isn't told from `null` and both give
             * `false`.
             *
             * @param value The value to test.
             */
            @JvmStatic
            fun isUndefined(value: Any?): Boolean =
                ScriptArgs.isUndefined(value)

            /**
             * Runs [func] once without arguments and says how long it took, in nanoseconds.
             *
             * What the function returns is dropped; an error it throws is passed on.
             *
             * @param func The function to time.
             * @throws IllegalArgumentException `func must be a function` when it isn't one.
             */
            @JvmStatic
            fun measureNanoTime(func: Any?): Long {
                val start = System.nanoTime()
                callFunction0(func)
                return System.nanoTime() - start
            }

            /**
             * Runs [func] once without arguments and says how long it took, in milliseconds.
             *
             * What the function returns is dropped; an error it throws is passed on.
             *
             * @param func The function to time.
             * @throws IllegalArgumentException `func must be a function` when it isn't one.
             */
            @JvmStatic
            fun measureTimeMillis(func: Any?): Long {
                val start = System.currentTimeMillis()
                callFunction0(func)
                return System.currentTimeMillis() - start
            }

            private fun callFunction0(func: Any?) {
                when {
                    func is NeonValue && func.isFunction -> func.call()
                    func is org.mozilla.javascript.Function -> {
                        val cx = RhinoContext.enter()
                        try {
                            val scope = ScriptableObject.getTopLevelScope(func)
                            func.call(cx, scope, scope, emptyArray())
                        } finally {
                            RhinoContext.exit()
                        }
                    }
                    func is Function0<*> -> func()
                    else -> throw IllegalArgumentException("func must be a function")
                }
            }

            /**
             * [value] as text without the right-to-left override character (U+202E) that can make a name read
             * backwards.
             *
             * @param value The text; `null` and `undefined` are taken as `null`.
             */
            @JvmStatic
            fun removeRLO(value: Any?): String =
                UtilsSupport.removeRLO(ScriptArgs.stringify(value))

            /**
             * The text of [html] without its tags: entities decoded and runs of whitespace collapsed.
             *
             * @param html The HTML; `null` and `undefined` are taken as `null`.
             */
            @JvmStatic
            fun removeTags(html: Any?): String =
                Jsoup.parse(ScriptArgs.stringify(html)).text()

            /**
             * Shows [msg] as a short toast, without waiting for it to be shown.
             *
             * @param msg The text; `null` and `undefined` are shown as `null`.
             */
            @JvmStatic
            fun toast(msg: Any?) {
                val text = ScriptArgs.stringify(msg)
                val show = Runnable {
                    Toast.makeText(GlobalApplication.requireContext(), text, Toast.LENGTH_SHORT).show()
                }
                if (Looper.myLooper() == Looper.getMainLooper())
                    show.run()
                else
                    Handler(Looper.getMainLooper()).post(show)
            }

            /**
             * Vibrates the device for [duration] seconds.
             *
             * A missing, zero or negative duration does nothing, and so does a device without a vibrator; a fraction is
             * cut off (`1.5` is `1` second).
             *
             * @param duration The time, in seconds.
             */
            @Suppress("DEPRECATION")
            @JvmStatic
            fun vibrate(duration: Any?) {
                try {
                    val seconds = ScriptArgs.long(duration, "duration", 0L).coerceAtLeast(0L)
                    val context = GlobalApplication.requireContext()
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                    else
                        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (seconds > 0 && vibrator.hasVibrator())
                        vibrator.vibrate(VibrationEffect.createOneShot(seconds * 1000, VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            /**
             * [xml] as pretty-printed JSON that mirrors its structure.
             *
             * Each element becomes an object with `localname`, `namespace` when it has one, `attributes` (a name to
             * value object) when it has some and `content` when it has children. `content` is an array of the child
             * elements in the same form and of the text between them as strings; whitespace-only text is left out.
             *
             * @param xml The XML text; `null` and `undefined` are taken as `null`, which isn't XML.
             * @throws IllegalArgumentException When [xml] isn't well-formed.
             */
            @JvmStatic
            fun xmlToJson(xml: Any?): String =
                UtilsSupport.xmlToJson(ScriptArgs.stringify(xml))

            /**
             * A string of 500 zero-width spaces (`​`).
             *
             * Put in a message, it folds the text after it behind KakaoTalk's 전체보기 (see more). Scripts can also
             * call it as `Utils.getLw()`.
             */
            @JvmStatic
            val lw: String by lazy { "​".repeat(500) }

            /**
             * [lw] followed by a line break, a rule of 20 `─` and another line break. Scripts can also call it as
             * `Utils.getLwLined()`.
             */
            @JvmStatic
            val lwLined: String by lazy { lw + "\n" + "─".repeat(20) + "\n" }
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
            name = "compress"
            returns = String::class.java
        },
        function {
            name = "copyToClipboard"
            args = arrayOf(Any::class.java)
            returns = Boolean::class.java
        },
        function {
            name = "delay"
            args = arrayOf(Long::class.java)
        },
        function {
            name = "sleep"
            args = arrayOf(Long::class.java)
        },
        function {
            name = "randomAlphanumeric"
            args = arrayOf(Int::class.java)
            returns = String::class.java
        },
        function {
            name = "getRandomUserAgent"
            returns = String::class.java
        },
        function {
            name = "isUndefined"
            args = arrayOf(Any::class.java)
            returns = Boolean::class.java
        },
        function {
            name = "measureNanoTime"
            args = arrayOf(Any::class.java)
            returns = Long::class.java
        },
        function {
            name = "measureTimeMillis"
            args = arrayOf(Any::class.java)
            returns = Long::class.java
        },
        function {
            name = "removeRLO"
            args = arrayOf(Any::class.java)
            returns = String::class.java
        },
        function {
            name = "removeTags"
            args = arrayOf(Any::class.java)
            returns = String::class.java
        },
        function {
            name = "toast"
            args = arrayOf(Any::class.java)
        },
        function {
            name = "vibrate"
            args = arrayOf(Long::class.java)
        },
        function {
            name = "xmlToJson"
            args = arrayOf(String::class.java)
            returns = String::class.java
        },
        function {
            name = "getLw"
            returns = String::class.java
        },
        function {
            name = "getLwLined"
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
