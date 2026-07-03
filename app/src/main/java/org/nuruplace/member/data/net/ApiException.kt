// Friendly error text from a failed call — reads the backend error envelope
// ({ "error": { "message" } } or { "message" }) off a Retrofit HttpException, and
// gives a plain "you're offline" for transport failures. Mirrors the iOS
// APIError.errorDescription.
package org.nuruplace.member.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

object ApiException {
    private val json = Json { ignoreUnknownKeys = true }

    fun message(e: Throwable): String = when (e) {
        is HttpException -> {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            parseEnvelope(body) ?: when (e.code()) {
                401 -> "Your session has expired. Please sign in again."
                else -> "Something went wrong (${e.code()})."
            }
        }
        is IOException -> "You appear to be offline."
        else -> e.message ?: "Something went wrong."
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
