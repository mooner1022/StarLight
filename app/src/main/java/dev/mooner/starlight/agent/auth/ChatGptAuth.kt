package dev.mooner.starlight.agent.auth

import dev.mooner.starlight.agent.AgentSettings
import dev.mooner.starlight.agent.AgentSettings.ChatGptKeys
import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.net.Http
import dev.mooner.starlight.agent.net.HttpStatusException
import dev.mooner.starlight.agent.provider.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.UUID

/** Thrown when ChatGPT needs the user to sign in (again). */
class SignInRequiredException(message: String) : IOException(message)

/**
 * "Sign in with ChatGPT" for open-source apps, which lets requests use the user's ChatGPT plan through the public
 * Responses API: https://developers.openai.com/siwc/token-sharing-open-source/sign-in
 *
 * The browser returns to a listener on 127.0.0.1, as the flow requires; the first sign-in registers a client for
 * this device, whose id is kept for later sign-ins and refreshes.
 */
class ChatGptAuth internal constructor(private val settings: AgentSettings) {

    private val refreshLock = Mutex()

    val isSignedIn: Boolean
        get() = settings.secrets.has(ChatGptKeys.REFRESH)

    val email: String?
        get() = settings.string(ChatGptKeys.EMAIL)

    /** One sign-in attempt: open [url] in the browser, then [await] the result. */
    inner class Attempt internal constructor(
        private val server: ServerSocket,
        private val verifier: String,
        private val state: String,
        private val nonce: String,
        private val savedClientId: String?,
        val url: String,
        private val redirectUri: String,
    ) {

        /** Waits for the browser's redirect, then trades the code for tokens and saves them. */
        suspend fun await(callbackPage: (success: Boolean, message: String?) -> String) {
            val callback = coroutineScope {
                // accept() doesn't notice cancellation; closing the socket ends it.
                val watcher = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        close()
                    }
                }
                try {
                    withContext(Dispatchers.IO) { receiveCallback(callbackPage) }
                } finally {
                    watcher.cancel()
                }
            }
            try {
                callback.error?.let { throw SignInRequiredException(it) }
                if (savedClientId != null && callback.clientId != null && callback.clientId != savedClientId)
                    throw SignInRequiredException("ChatGPT returned a different client for this device. Sign in again.")
                val clientId = savedClientId ?: callback.clientId
                    ?: throw SignInRequiredException("ChatGPT didn't return a client id. Sign in again.")
                val tokens = Http.postForm(TOKEN_URL, emptyMap(), mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to clientId,
                    "code" to callback.code!!,
                    "code_verifier" to verifier,
                    "redirect_uri" to redirectUri,
                    "resource" to RESOURCE,
                )).let { AgentJson.parseToJsonElement(it).jsonObject }
                val scopes = tokens.string("scope").orEmpty().split(' ')
                if (TOKEN_SHARING_SCOPE !in scopes)
                    throw SignInRequiredException("ChatGPT didn't allow plan usage for this app. Sign in again and allow it.")
                val idToken = tokens.string("id_token") ?: throw SignInRequiredException("ChatGPT didn't return an ID token.")
                val claims = verifyIdToken(idToken, clientId, nonce)
                settings.putString(ChatGptKeys.CLIENT_ID, clientId)
                settings.putString(ChatGptKeys.EMAIL, claims.string("email"))
                settings.secrets.put(ChatGptKeys.ID_TOKEN, idToken)
                save(tokens)
                settings.notifyChanged()
            } finally {
                close()
            }
        }

        fun close() {
            runCatching { server.close() }
        }

        private fun receiveCallback(page: (Boolean, String?) -> String): Callback {
            server.soTimeout = SIGN_IN_TIMEOUT_MILLIS
            while (true) {
                val socket = try {
                    server.accept()
                } catch (e: SocketTimeoutException) {
                    throw SignInRequiredException("Sign-in timed out.")
                }
                socket.use {
                    val requestLine = it.getInputStream().bufferedReader().readLine().orEmpty()
                    val target = requestLine.split(' ').getOrNull(1).orEmpty()
                    if (!target.startsWith(CALLBACK_PATH)) {
                        it.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        return@use
                    }
                    val query = parseQuery(target.substringAfter('?', ""))
                    val callback = when {
                        query["state"] != state -> Callback(error = "The sign-in response didn't match this attempt.")
                        query["error"] != null  -> Callback(error = query["error_description"] ?: query["error"])
                        query["code"] == null   -> Callback(error = "ChatGPT didn't return an authorization code.")
                        else                    -> Callback(code = query["code"], clientId = query["client_id"])
                    }
                    val html = page(callback.error == null, callback.error).toByteArray()
                    val status = if (callback.error == null) "200 OK" else "400 Bad Request"
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(html)
                        flush()
                    }
                    return callback
                }
            }
        }
    }

    private class Callback(val code: String? = null, val clientId: String? = null, val error: String? = null)

    /** Starts listening on a loopback port and builds the authorization URL for it. */
    suspend fun begin(appName: String): Attempt = withContext(Dispatchers.IO) {
        val server = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))
        val redirectUri = "http://$LOOPBACK:${server.localPort}$CALLBACK_PATH"
        val verifier = randomToken(48)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = randomToken(32)
        val nonce = randomToken(32)
        val savedClientId = settings.string(ChatGptKeys.CLIENT_ID)
        val hostId = settings.string(ChatGptKeys.HOST_ID)
            ?: "urn:uuid:${UUID.randomUUID()}".also { settings.putString(ChatGptKeys.HOST_ID, it) }
        val params = buildMap {
            put("client_id", savedClientId ?: REGISTRATION_CLIENT_ID)
            if (savedClientId == null)
                put("agent_name_hint", appName)
            put("ext_agent_host_id", hostId)
            settings.secrets.get(ChatGptKeys.ID_TOKEN)?.let { put("id_token_hint", it) }
            put("response_type", "code")
            put("redirect_uri", redirectUri)
            put("scope", SCOPE)
            put("resource", RESOURCE)
            put("state", state)
            put("nonce", nonce)
            put("code_challenge_method", "S256")
            put("code_challenge", challenge)
        }
        val url = "$ISSUER/api/accounts/authorize?" + params.entries.joinToString("&") { (k, v) -> "$k=${Http.encode(v)}" }
        Attempt(server, verifier, state, nonce, savedClientId, url, redirectUri)
    }

    /** A valid access token, refreshed when it has expired or when [force] is set after a 401. */
    suspend fun accessToken(force: Boolean): String = refreshLock.withLock {
        val access = settings.secrets.get(ChatGptKeys.ACCESS)
        val expiresAt = settings.long(ChatGptKeys.EXPIRES_AT)
        val now = System.currentTimeMillis()
        if (access != null && !force && now < expiresAt - EXPIRY_MARGIN_MILLIS)
            return access
        // Don't refresh earlier than the server allows unless the token is unusable.
        if (access != null && force && now < settings.long(ChatGptKeys.EARLIEST_REFRESH_AT) && now < expiresAt)
            return access
        refresh()
    }

    private suspend fun refresh(): String {
        val refreshToken = settings.secrets.get(ChatGptKeys.REFRESH)
        val clientId = settings.string(ChatGptKeys.CLIENT_ID)
        if (refreshToken == null || clientId == null)
            throw SignInRequiredException("Sign in with ChatGPT first.")
        val response = try {
            Http.postForm(TOKEN_URL, emptyMap(), mapOf(
                "grant_type" to "refresh_token",
                "client_id" to clientId,
                "refresh_token" to refreshToken,
                "resource" to RESOURCE,
            ))
        } catch (e: HttpStatusException) {
            if (e.code == 400 || e.code == 401) {
                // The refresh token expired, was reused or was revoked: only a new sign-in helps.
                signOut(keepClient = true)
                throw SignInRequiredException("The ChatGPT sign-in expired. Sign in again.")
            }
            throw e
        }
        val tokens = AgentJson.parseToJsonElement(response).jsonObject
        return save(tokens)
    }

    private fun save(tokens: JsonObject): String {
        val access = tokens.string("access_token") ?: throw SignInRequiredException("ChatGPT didn't return an access token.")
        val now = System.currentTimeMillis()
        val expiresIn = tokens["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        settings.secrets.put(ChatGptKeys.ACCESS, access)
        tokens.string("refresh_token")?.let { settings.secrets.put(ChatGptKeys.REFRESH, it) }
        tokens.string("id_token")?.let { settings.secrets.put(ChatGptKeys.ID_TOKEN, it) }
        settings.putLong(ChatGptKeys.EXPIRES_AT, now + expiresIn * 1000)
        settings.putLong(ChatGptKeys.EARLIEST_REFRESH_AT, parseInstant(tokens["earliest_refresh_at"]) ?: 0)
        return access
    }

    /** Forgets the tokens; the registered client id stays so that the next sign-in reuses it. */
    fun signOut(keepClient: Boolean = true) {
        settings.secrets.put(ChatGptKeys.ACCESS, null)
        settings.secrets.put(ChatGptKeys.REFRESH, null)
        settings.putLong(ChatGptKeys.EXPIRES_AT, 0)
        if (!keepClient) {
            settings.secrets.put(ChatGptKeys.ID_TOKEN, null)
            settings.putString(ChatGptKeys.CLIENT_ID, null)
        }
        settings.putString(ChatGptKeys.EMAIL, null)
        settings.notifyChanged()
    }

    /** Checks the ID token's RS256 signature against OpenAI's keys and its claims against this attempt. */
    private suspend fun verifyIdToken(token: String, clientId: String, nonce: String): JsonObject {
        val segments = token.split('.')
        if (segments.size != 3)
            throw SignInRequiredException("ChatGPT returned a malformed ID token.")
        val header = AgentJson.parseToJsonElement(base64UrlDecode(segments[0]).decodeToString()).jsonObject
        val claims = AgentJson.parseToJsonElement(base64UrlDecode(segments[1]).decodeToString()).jsonObject
        if (header.string("alg") != "RS256")
            throw SignInRequiredException("ChatGPT returned an ID token with an unexpected algorithm.")
        val keys = AgentJson.parseToJsonElement(Http.get(JWKS_URL, emptyMap())).jsonObject["keys"]?.jsonArray.orEmpty()
        val jwk = keys.map { it.jsonObject }.firstOrNull { it.string("kid") == header.string("kid") }
            ?: throw SignInRequiredException("ChatGPT's signing key wasn't found.")
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(1, base64UrlDecode(jwk.string("n")!!)), BigInteger(1, base64UrlDecode(jwk.string("e")!!)))
        )
        val valid = Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update("${segments[0]}.${segments[1]}".toByteArray())
            verify(base64UrlDecode(segments[2]))
        }
        val audience = when (val aud = claims["aud"]) {
            is JsonArray     -> aud.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(aud.contentOrNull)
            else             -> emptyList()
        }
        val expiresAt = claims["exp"]?.jsonPrimitive?.longOrNull ?: 0
        when {
            !valid                                     -> throw SignInRequiredException("The ID token's signature is invalid.")
            claims.string("iss") != ISSUER             -> throw SignInRequiredException("The ID token comes from an unexpected issuer.")
            clientId !in audience                      -> throw SignInRequiredException("The ID token is for another client.")
            expiresAt * 1000 < System.currentTimeMillis() -> throw SignInRequiredException("The ID token has expired.")
            claims.string("nonce") != nonce            -> throw SignInRequiredException("The ID token doesn't match this sign-in.")
            claims.string("sub").isNullOrBlank()       -> throw SignInRequiredException("The ID token has no subject.")
        }
        return claims
    }

    companion object {
        private const val ISSUER = "https://auth.openai.com"
        private const val TOKEN_URL = "$ISSUER/api/accounts/oauth/token"
        private const val JWKS_URL = "$ISSUER/.well-known/jwks.json"
        private const val RESOURCE = "https://api.openai.com/v1"
        private const val REGISTRATION_CLIENT_ID = "dynamic_agent_client"
        private const val TOKEN_SHARING_SCOPE = "chatgpt.tokens.use.direct"
        private const val SCOPE = "openid profile email offline_access resource.invoke $TOKEN_SHARING_SCOPE"
        // The flow requires the loopback address itself, not "localhost".
        private const val LOOPBACK = "127.0.0.1"
        private const val CALLBACK_PATH = "/auth/callback"
        private const val SIGN_IN_TIMEOUT_MILLIS = 10 * 60 * 1000
        private const val EXPIRY_MARGIN_MILLIS = 60_000L

        /** Where users see and manage what this app uses of their plan. */
        const val MANAGE_USAGE_URL = "https://chatgpt.com/settings/usage"

        private val random = SecureRandom()

        private fun randomToken(bytes: Int): String =
            base64Url(ByteArray(bytes).also(random::nextBytes))

        private fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun base64UrlDecode(value: String): ByteArray =
            Base64.getUrlDecoder().decode(value.trimEnd('='))

        private fun parseQuery(query: String): Map<String, String> =
            query.split('&').filter(String::isNotEmpty).associate { pair ->
                val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
                val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                key to value
            }

        /** Seconds or milliseconds since the epoch, or an ISO 8601 time. */
        private fun parseInstant(value: kotlinx.serialization.json.JsonElement?): Long? {
            val primitive = value as? JsonPrimitive ?: return null
            primitive.longOrNull?.let { return if (it < 10_000_000_000) it * 1000 else it }
            return primitive.contentOrNull?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        }
    }
}
