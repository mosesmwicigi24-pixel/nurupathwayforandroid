// Friendly error text from a failed call — reads the backend error envelope
// ({ "error": { "message" } } or { "message" }) off a Retrofit HttpException, and
// gives a plain "you're offline" for transport failures. Mirrors the iOS
// APIError.errorDescription.
package org.nuruplace.member.data.net

import android.content.Context
import kotlinx.serialization.json.Json
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
            parseEnvelope(body) ?: when (e.code()) {
                401 -> "Your session has expired. Please sign in again."
                else -> "Something went wrong (${e.code()})."
            }
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
            is SocketTimeoutException -> "Nuru Place is taking too long to respond. Please try again."
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
}
