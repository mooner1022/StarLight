package dev.mooner.starlight.media.media

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * A minimal HTTP/1.1 server for tests: one response per connection. `com.sun.net.httpserver` isn't on the
 * classpath of Android unit tests.
 */
class TestHttpServer : AutoCloseable {

    class Request(val path: String, val headers: Map<String, String>)

    class Response(val status: Int, val body: ByteArray = ByteArray(0), val headers: Map<String, String> = emptyMap())

    private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val pool = Executors.newCachedThreadPool()
    private val routes: MutableMap<String, (Request) -> Response> = linkedMapOf()

    val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

    fun route(path: String, handler: (Request) -> Response) {
        routes[path] = handler
    }

    fun start() {
        thread(isDaemon = true, name = "TestHttpServer") {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                pool.execute { serve(client) }
            }
        }
    }

    private fun serve(client: Socket) {
        client.use {
            val input = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = input.readLine() ?: return
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
            }
            val path = requestLine.split(' ').getOrElse(1) { "/" }.substringBefore('?')
            val response = routes[path]?.invoke(Request(path, headers)) ?: Response(404, "not found".toByteArray())
            val out = client.getOutputStream()
            val head = buildString {
                append("HTTP/1.1 ${response.status} ${reason(response.status)}\r\n")
                for ((name, value) in response.headers)
                    append("$name: $value\r\n")
                append("Content-Length: ${response.body.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(head.toByteArray(Charsets.UTF_8))
            out.write(response.body)
            out.flush()
        }
    }

    private fun reason(status: Int): String =
        when (status) {
            200  -> "OK"
            302  -> "Found"
            404  -> "Not Found"
            else -> "Status"
        }

    override fun close() {
        socket.close()
        pool.shutdownNow()
    }
}
