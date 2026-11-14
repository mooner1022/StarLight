/*
 * HttpApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.languages.neonjs.whileWaitingForAnotherThread
import dev.mooner.starlight.media.script.ScriptValues
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.script.runCallback
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HttpApi: Api<HttpApi.Http>() {

    /**
     * MessengerBot R's `Http`: web requests, with the page parsed by jsoup (`request`) or the answer kept whole
     * (`fetch`); a global in both legacy and API2 projects.
     *
     * Each of the two has a form that waits for the answer (`requestSync`, `fetchSync`) and one that calls a function
     * when it arrives (`request`, `fetch`). The first argument of all four is a URL string or an object of options.
     * Callbacks run as one of the project's tasks, as a timer's does: an error they throw is logged and doesn't
     * unload the project.
     *
     * Everything a project has under way is cancelled when the project is compiled again or unloaded, and a callback
     * never runs after that. `fetch` and `fetchSync` calls are aborted on the network. A `request` that waits in the
     * queue is dropped; one already sending is interrupted, which a blocked socket read may not notice, so it ends
     * when its timeout does (at most 120 s) and only its callback is dropped. `requestSync` is not aborted. A script
     * that kept the old `Http` gets `IllegalStateException: Http is closed` from
     * `request`, `fetch` and `fetchSync`.
     *
     * ```js
     * Http.request("https://example.com", (error, response, doc) => {
     *     if (error) return Log.e("http", String(error));
     *     Log.i("http", doc.title());
     * });
     * Http.fetch({ url: "https://example.com/api", method: "POST", json: { a: 1 } }, (error, res) => {
     *     if (!error && res.ok) Log.i("http", JSON.stringify(res.json()));
     * });
     * ```
     */
    class Http internal constructor(
        private val project: Project,
        private val json: ScriptJson,
    ) {

        private val closed = AtomicBoolean(false)

        private val observing = AtomicBoolean(false)

        private val outstanding = AtomicInteger()

        private val calls: MutableSet<CallHandle> = ConcurrentHashMap.newKeySet()

        private val legacyTasks: MutableSet<Future<*>> = ConcurrentHashMap.newKeySet()

        /**
         * Sends a GET, or the request [urlOrOption] describes, with jsoup, and calls [callback] with the page when it
         * has arrived. It returns at once.
         *
         * [urlOrOption] is a URL string, or an object with these options; any other key is ignored:
         * - `url`: the address, required, a string.
         * - `timeout`: milliseconds, a whole number; `3000` when not given. `0`, a negative number or one above 120000 means 120000.
         * - `method`: `"GET"` when not given; `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS` or `TRACE`.
         * - `headers`: an object whose values are strings, numbers or booleans.
         * - `body`: a string to send. `GET`, `HEAD`, `OPTIONS` and `TRACE` can't have one.
         *
         * With a URL string the request also says it comes from a browser and from `https://www.google.com`; with an
         * object it doesn't, unless `headers` do. Any content type is parsed as HTML. Redirects are followed, and at
         * most 2 MiB of the body is read. An HTTP error status counts as a failure.
         *
         * [callback] gets `(error, response, doc)`:
         * - On success, `null`, the `org.jsoup.Connection.Response` and the `org.jsoup.nodes.Document`.
         * - When the request fails, including by an HTTP error status or a bad address: the Java exception, `null`
         *   and `null`.
         * - When the answer can't be parsed: the exception, the response and `null`.
         *
         * Up to 8 requests run at once, and 64 more wait. Errors in the options are thrown by `request` itself.
         *
         * @param urlOrOption The URL or the options.
         * @param callback Called with `(error, response, doc)`.
         * @throws IllegalArgumentException When the options are wrong, such as a missing or non-string `url`, a
         * `timeout` that isn't a whole number, or a `body` that isn't a string.
         * @throws IllegalStateException `Http is closed` when the project was compiled again after this `Http` was
         * got.
         * @throws java.util.concurrent.RejectedExecutionException When too many requests are waiting.
         */
        fun request(urlOrOption: Any?, callback: (error: Throwable?, response: Connection.Response?, doc: Document?) -> Unit) {
            val connection = LegacyRequests.connection(urlOrOption)
            ensureObserving()
            checkOpen()
            val task = object : FutureTask<Unit>({
                if (!closed.get()) {
                    var response: Connection.Response? = null
                    var document: Document? = null
                    var error: Throwable? = null
                    try {
                        val received = whileWaitingForAnotherThread { connection.execute() }
                        response = received
                        document = received.parse()
                    } catch (e: Exception) {
                        error = e
                    }
                    deliver("Http.request") { callback(error, response, document) }
                }
            }, Unit) {
                override fun done() {
                    legacyTasks -= this
                }
            }
            legacyTasks += task
            try {
                LegacyRequests.pool.execute(task)
            } catch (e: RejectedExecutionException) {
                legacyTasks -= task
                throw e
            }
            if (closed.get())
                cancelTask(task)
        }

        private fun cancelTask(task: Future<*>) {
            task.cancel(true)
            LegacyRequests.pool.remove(task as Runnable)
        }

        /**
         * Sends a GET, or the request [urlOrOption] describes, with jsoup, waits for the answer and returns the parsed
         * page.
         *
         * The options and defaults are those of `request`. The script waits, so keep the timeout short. It works
         * after the project was compiled again, which `request` doesn't.
         *
         * @param urlOrOption The URL or the options.
         * @return The page, an `org.jsoup.nodes.Document`.
         * @throws IllegalArgumentException When the options are wrong.
         * @throws java.io.IOException When the request fails, including by an HTTP error status or a timeout.
         */
        fun requestSync(urlOrOption: Any?): Document =
            LegacyRequests.connection(urlOrOption).let { connection ->
                whileWaitingForAnotherThread { connection.execute() }.parse()
            }

        /**
         * Sends a request and calls [callback] with the whole answer when it has arrived. It returns at once with an
         * [HttpCall] that can cancel it.
         *
         * [urlOrOption] is a URL string, or an object with these options (a wrong type throws
         * `IllegalArgumentException`, with a message such as `timeout must be an integer`):
         * - `url`: an `http` or `https` address, required, a string.
         * - `method`: `"GET"` when not given; `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE` or `OPTIONS`, in any
         *   case. `GET` and `HEAD` can't have a body. `POST`, `PUT` and `PATCH` without one send an empty body.
         * - `headers`: an object whose values are strings, numbers or booleans.
         * - `query`: an object of the same kind, added to the address as query parameters, encoded.
         * - `body`: a string, sent as UTF-8; its `Content-Type` is `text/plain; charset=utf-8` unless `headers` have one.
         * - `json`: any value with a JSON form, sent as `JSON.stringify` writes it, as `application/json; charset=utf-8`.
         * - `form`: an object of strings, numbers and booleans, sent as a URL-encoded form.
         * - `timeout`: milliseconds for the whole call, from 1 to 120000; `10000` when not given. A string such as
         *   `"5000"` isn't accepted.
         * - `maxResponseBytes`: the largest body to accept, from 1 to 33554432; 8 MiB when not given.
         *
         * Only one of `body`, `json` and `form` can be given. Redirects are followed. A status such as 404 or 500
         * isn't an error: the callback gets the response.
         *
         * [callback] gets `(error, response)`: `null` and the [HttpResponse], or a Java exception and `null`. The
         * exception is an `IOException` when the network fails, the time runs out (`timeout`), the request is
         * cancelled (`Canceled`), or the body is over the limit (`Response exceeds maxResponseBytes (N)`). It isn't
         * called after the project is compiled again or unloaded.
         *
         * A project can have 64 calls under way.
         *
         * @param urlOrOption The URL or the options.
         * @param callback Called with `(error, response)`.
         * @return The call, to cancel it.
         * @throws IllegalArgumentException When the options are wrong.
         * @throws IllegalStateException `Http is closed` after the project was compiled again, or
         * `Too many HTTP requests for this script (64)`.
         */
        fun fetch(urlOrOption: Any?, callback: (error: Throwable?, response: HttpResponse?) -> Unit): HttpCall {
            val spec = FetchSpec.parse(urlOrOption, json)
            ensureObserving()
            val handle = begin()
            try {
                FetchPool.executor.execute {
                    var response: HttpResponse? = null
                    var error: Throwable? = null
                    try {
                        response = HttpResponse(HttpExecutor.execute(spec, handle), json)
                    } catch (e: Exception) {
                        error = e
                    } finally {
                        end(handle)
                    }
                    deliver("Http.fetch") { callback(error, response) }
                }
            } catch (e: RejectedExecutionException) {
                end(handle)
                throw e
            }
            return HttpCall(handle)
        }

        /**
         * Sends a request, waits for the answer and returns it. The options are those of `fetch`.
         *
         * The script waits for the whole call, up to its `timeout`. Any status is an answer, including 404 and 500.
         *
         * @param urlOrOption The URL or the options.
         * @return The response.
         * @throws IllegalArgumentException When the options are wrong.
         * @throws java.io.IOException When the network fails, the time runs out, or the body is over the limit.
         * @throws IllegalStateException `Http is closed` after the project was compiled again, or
         * `Too many HTTP requests for this script (64)`.
         */
        fun fetchSync(urlOrOption: Any?): HttpResponse {
            val spec = FetchSpec.parse(urlOrOption, json)
            ensureObserving()
            val handle = begin()
            try {
                return HttpResponse(whileWaitingForAnotherThread { HttpExecutor.execute(spec, handle) }, json)
            } finally {
                end(handle)
            }
        }

        private fun checkOpen() {
            if (closed.get())
                throw IllegalStateException("Http is closed")
        }

        private fun begin(): CallHandle {
            checkOpen()
            if (outstanding.incrementAndGet() > MAX_CALLS) {
                outstanding.decrementAndGet()
                throw IllegalStateException("Too many HTTP requests for this script ($MAX_CALLS)")
            }
            val handle = CallHandle()
            calls += handle
            if (closed.get()) {
                end(handle)
                throw IllegalStateException("Http is closed")
            }
            return handle
        }

        private fun end(handle: CallHandle) {
            if (calls.remove(handle))
                outstanding.decrementAndGet()
        }

        private fun deliver(name: String, block: () -> Unit) {
            if (closed.get())
                return
            project.runCallback(name, { error ->
                translate {
                    Locale.ENGLISH { "$name's callback failed: $error" }
                    Locale.KOREAN  { "${name}의 콜백에서 오류가 났어요: $error" }
                }
            }) {
                if (!closed.get())
                    block()
            }
        }

        private fun cancelAll() {
            calls.forEach(CallHandle::cancel)
            legacyTasks.forEach(::cancelTask)
        }

        private fun close() {
            closed.set(true)
            cancelAll()
        }

        private fun ensureObserving() {
            if (observing.compareAndSet(false, true))
                project.getLifecycle().registerObserver(Observer())
        }

        /** What a project has under way belongs to the code that started it: compiling again or unloading ends it. */
        private inner class Observer : ProjectLifecycleObserver.ExplicitObserver {

            override fun onCompileStart(project: Project) = finish(project)

            override fun onDestroy(project: Project) = finish(project)

            override fun onDisable(project: Project) = cancelAll()

            private fun finish(project: Project) {
                close()
                project.getLifecycle().unregisterObserver(this)
            }
        }

        override fun toString(): String =
            "Http"
    }

    override val name: String = "Http"

    override val objects: List<ApiObject> =
        getApiObjects<Http>()

    override val instanceClass: Class<Http> =
        Http::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        Http(project, ScriptJson.current())

    private companion object {
        const val MAX_CALLS = 64
    }
}

/** The threads `Http.fetch` sends on; each project's own limit keeps their number down. */
private object FetchPool {

    private val count = AtomicInteger()

    val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "StarLight-Http-${count.incrementAndGet()}").apply { isDaemon = true }
    }
}

/** `Http.request` and `Http.requestSync`: jsoup connections from the options. */
private object LegacyRequests {

    private const val DEFAULT_TIMEOUT = 3000
    private const val MAX_TIMEOUT = 120000
    private const val REFERRER = "https://www.google.com"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36 StarLight"

    private val count = AtomicInteger()

    /** Two threads, up to eight when 64 requests wait; one more is refused. */
    val pool = ThreadPoolExecutor(2, 8, 30L, TimeUnit.SECONDS, LinkedBlockingQueue(64),
        { task -> Thread(task, "StarLight-HttpLegacy-${count.incrementAndGet()}").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    fun connection(param: Any?): Connection {
        val (urlText, options) = RequestOptions.split(param).let { it.url to it.options }
        val url = urlText ?: RequestOptions.url(options)
        val timeout = RequestOptions.int(options, "timeout", DEFAULT_TIMEOUT).let {
            if (it <= 0 || it > MAX_TIMEOUT) MAX_TIMEOUT else it
        }
        val method = RequestOptions.string(options, "method", "GET").uppercase(java.util.Locale.ROOT)
        val headers = RequestOptions.map(options, "headers")
        val body = options?.let { ScriptValues.member(it, "body") }
        if (body != null && body !is CharSequence)
            throw IllegalArgumentException("body must be a string")

        val connection = Jsoup.connect(url)
            .ignoreContentType(true)
            .timeout(timeout)
            .method(Connection.Method.valueOf(method))
        if (urlText != null)
            connection.userAgent(USER_AGENT).referrer(REFERRER)
        for ((name, value) in headers)
            connection.header(name, value)
        if (body != null)
            connection.requestBody(body.toString())
        return connection
    }
}
