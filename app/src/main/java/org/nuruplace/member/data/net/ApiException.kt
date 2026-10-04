// Member-facing words for a failed call, in the one state language (pathway
// docs/EXPERIENCE.md §4 — StateLanguage.kt): reads the backend error envelope
// ({ "error": { "message" } } or { "message" }) off a Retrofit HttpException
// for a refusal's own words, and asks the phone whether it is really offline
// before saying so. Raw server or exception text never comes out of here.
package org.nuruplace.member.data.net

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

object ApiException {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The shared state (§4) for a failed call — a title, a line and the one
     * action — for a screen that shows a whole state card. Reads an HTTP
     * error's body (a one-shot stream) for a refusal's own words. With
     * [context] (else the app's own), the OS network state decides between
     * "You're offline" and "Something went wrong on our side": a member on
     * full WiFi/4G is never told the fault is their connection.
     */
    fun state(e: Throwable, context: Context? = null): StateMessage =
        StateLanguage.forError(e, deviceOnline(context), (e as? HttpException)?.let(::refusalWords))

    /**
     * The one-line form of [state], for a note under a button or a toast.
     * [credentials]: the call carried a password or a code the member typed
     * (sign-in, two-step codes) — its 401 is a refusal in the server's own
     * words ("Invalid email or password"), not an ended session.
     */
    fun message(e: Throwable, context: Context? = null, credentials: Boolean = false): String {
        if (credentials && e is HttpException && e.code() == 401) {
            return envelopeWords(e) ?: StateLanguage.sessionEnded.sentence
        }
        return state(e, context).sentence
    }

    /** The phone's own network state: from [context] when given, else from
     *  the app's HTTP stack; null when neither can say (a JVM test). */
    private fun deviceOnline(context: Context?): Boolean? =
        context?.let { NetworkStatus.isOnline(it) } ?: runCatching { Net.client.deviceOnline() }.getOrNull()

    private fun envelopeWords(e: HttpException): String? =
        parseEnvelope(runCatching { e.response()?.errorBody()?.string() }.getOrNull())

    /** A refusal's own words — only from OUR envelope (`{ error: { code,
     *  message } }`): a bare `{ message }`, a proxy's page or an empty body is
     *  not something our API said to the member (iOS NuruStateCopy, the same
     *  rule) — and neither is VALIDATION_FAILED ([ServerError.refusalWords]). */
    private fun refusalWords(e: HttpException): String? =
        parseServerError(e.code(), runCatching { e.response()?.errorBody()?.string() }.getOrNull()).refusalWords

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

}

/** The envelope code of a request the server could not parse: the APP built
 *  it wrong, so it is our side's fault, not a refusal in a member's words
 *  (EXPERIENCE.md §7.3) — "Request body failed validation" is never shown. */
const val VALIDATION_FAILED = "VALIDATION_FAILED"

/** A server refusal, as [ApiException.serverError] read it. */
data class ServerError(
    val status: Int,
    /** The envelope's machine code (e.g. GIFT_IN_PROGRESS); null when absent. */
    val code: String?,
    /** The envelope's words — member-facing only as [refusalWords]. Null
     *  when the body carried none. */
    val message: String?,
    val details: JsonObject?,
) {
    /** The server's own words for the member: a refusal in our envelope (a
     *  4xx with its code) — never [VALIDATION_FAILED]'s parse message, a
     *  5xx's "Internal server error", or a proxy's page. Null otherwise. */
    val refusalWords: String?
        get() = message?.takeIf { code != null && code != VALIDATION_FAILED && status in 400..499 && status != 401 }

    /** What the member reads (EXPERIENCE.md §4): [refusalWords] when there
     *  are any, else the state language's sentence for the status — "Something
     *  went wrong on our side. It isn't you — please try again in a moment.",
     *  "Your session has ended. …", "This isn't here any more. …". Never raw
     *  server text. */
    val displayMessage: String get() = StateLanguage.forStatus(status, refusalWords).sentence

    /** A string detail (`details.transaction_id`), null when absent or blank. */
    fun detail(key: String): String? =
        (details?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
