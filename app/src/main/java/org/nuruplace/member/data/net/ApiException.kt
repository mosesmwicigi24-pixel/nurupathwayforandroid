// Friendly error text from a failed call — reads the backend error envelope
// ({ "error": { "message" } } or { "message" }) off a Retrofit HttpException, and
// gives a plain "you're offline" for transport failures. Mirrors the iOS
// APIError.errorDescription.
package org.nuruplace.member.data.net

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object ApiException {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Friendly message for a failed call. Pass [context] and we consult the OS
     * network state so a member on full WiFi/4G is never wrongly told they're
     * "offline" — a transient reach/timeout/TLS failure reads as "couldn't
     * reach Nuru Place, try again" instead. Without context we fall back to
     * classifying by the exception type.
     */
    fun message(e: Throwable, context: Context? = null): String = when (e) {
        is HttpException -> {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            parseEnvelope(body) ?: statusMessage(e.code())
        }
        is IOException -> transportMessage(e, context)
        else -> e.message ?: "Something went wrong."
    }

    /**
     * A transport failure is only truly "offline" when the device has no
     * validated network. Otherwise the phone is online but we couldn't reach
     * the server (DNS blip, timeout, TLS reset, brief drop) — say so honestly.
     */
    private fun transportMessage(e: IOException, context: Context?): String {
        val online = context?.let { NetworkStatus.isOnline(it) }
        if (online == false) return "You appear to be offline. Check your connection and try again."
        return when (e) {
            // DNS didn't resolve with no known-good network → almost always offline.
            is UnknownHostException ->
                if (online == null) "You appear to be offline. Check your connection and try again."
                else "Couldn't reach Nuru Place. Please try again in a moment."
            // OkHttp says "connect timed out" when the server never answered the
            // handshake at all — the box is away (a recurring host outage, see
            // the pathway incident ledger), not the phone. Say so, so a tester
            // stops blaming their network. A read timeout is the slower kind.
            is SocketTimeoutException ->
                if (e.message?.contains("connect", ignoreCase = true) == true)
                    "Nuru Place can't be reached right now — that's on our side, not your phone. We'll keep trying; please try again in a few minutes."
                else "Nuru Place is taking too long to respond. Please try again."
            is SSLException -> "Secure connection failed. Please try again."
            is ConnectException -> "Couldn't reach Nuru Place. Please try again in a moment."
            else -> "Couldn't reach Nuru Place. Please check your connection and try again."
        }
    }

    private fun parseEnvelope(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: obj["message"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    /**
     * The server's error envelope — `{ error: { code, message, request_id,
     * details? } }` — off a failed call, for a caller that must act on the
     * CODE (Give: 409 GIFT_IN_PROGRESS follows `details.transaction_id`).
     * Null when no HTTP answer came back. An error body is a one-shot
     * stream: read it here ONCE and use [ServerError.displayMessage], never
     * [message] on the same exception afterwards.
     */
    fun serverError(e: Throwable): ServerError? {
        if (e !is HttpException) return null
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        return parseServerError(e.code(), body)
    }

    /** [serverError]'s parse, pure: an unreadable or absent body leaves
     *  code, message and details null — the status still stands. */
    fun parseServerError(status: Int, body: String?): ServerError {
        val obj = body?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val err = obj?.get("error") as? JsonObject
        fun str(o: JsonObject?, key: String): String? =
            ((o?.get(key)) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        return ServerError(
            status = status,
            code = str(err, "code"),
            message = str(err, "message") ?: str(obj, "message"),
            details = err?.get("details") as? JsonObject,
        )
    }

    /** What [message] says for an HTTP status when the body says nothing. */
    internal fun statusMessage(status: Int): String = when (status) {
        401 -> "Your session has expired. Please sign in again."
        else -> "Something went wrong ($status)."
    }
}

/** A server refusal, as [ApiException.serverError] read it. */
data class ServerError(
    val status: Int,
    /** The envelope's machine code (e.g. GIFT_IN_PROGRESS); null when absent. */
    val code: String?,
    /** Member-facing — shown as-is. Null when the body carried none. */
    val message: String?,
    val details: JsonObject?,
) {
    /** The server's words, else the status line [ApiException.message] gives. */
    val displayMessage: String get() = message ?: ApiException.statusMessage(status)

    /** A string detail (`details.transaction_id`), null when absent or blank. */
    fun detail(key: String): String? =
        (details?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
