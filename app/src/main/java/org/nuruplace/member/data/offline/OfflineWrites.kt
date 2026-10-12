// Write-through bridge between the screens and the offline queue. A call site
// wraps its normal online write in runOrQueue(...): the direct call is attempted
// first so the UI keeps the server-authoritative response when connected; only a
// TRANSPORT failure (no HTTP response — offline, DNS, timeout) falls through to
// the queue for later replay. An HTTP error (4xx/5xx) is a real rejection and is
// rethrown, never queued. Money is never routed here (§5.6).
package org.nuruplace.member.data.offline

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import java.io.IOException

@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal val snakeJson: Json = Json {
    encodeDefaults = true
    namingStrategy = JsonNamingStrategy.SnakeCase   // camelCase ↔ snake_case, same wire shape as the API
}

/** Serialize a request-body DTO to a snake_case JSON payload for the queue. */
inline fun <reified T> queuePayload(body: T): JsonObject =
    snakeJson.encodeToJsonElement(serializer<T>(), body).jsonObject

/**
 * Try [direct] online; if it fails at the transport layer, enqueue [domain]:[op]
 * with [payload] for replay and return null. HTTP errors propagate unchanged.
 * A money write is refused up front with [MoneyNeverQueued] — before [direct]
 * runs, so wrapping one here fails on its first use, online or not (§5.6).
 */
suspend fun <T> OfflineQueue.runOrQueue(
    domain: String,
    op: String,
    payload: JsonObject,
    direct: suspend () -> T,
): T? = try {
    requireQueueable(domain, op, payload)
    direct()
} catch (e: IOException) {
    // Transport failure only (Retrofit throws HttpException — NOT an IOException —
    // for non-2xx, so genuine server rejections skip the queue and rethrow).
    enqueue(domain, op, payload)
    null
}

/** What a member's write came to (EXPERIENCE.md §4, §1.7): the server has it,
 *  it waits in the offline queue, or it was refused — never an exception out
 *  of a tap. */
sealed interface WriteOutcome {
    object Sent : WriteOutcome
    object Queued : WriteOutcome
    data class Failed(val line: String) : WriteOutcome
}

/**
 * Run a write that may go to the offline queue: [send] returns null when it
 * was queued ([runOrQueue]'s contract). A refusal or a failure becomes
 * [WriteOutcome.Failed] in [failureLine]'s words; a cancel (the member left)
 * is let through, never shown.
 */
suspend fun queuedWrite(send: suspend () -> Any?, failureLine: (Throwable) -> String): WriteOutcome =
    try {
        if (send() == null) WriteOutcome.Queued else WriteOutcome.Sent
    } catch (c: kotlin.coroutines.cancellation.CancellationException) {
        throw c
    } catch (e: Exception) {
        WriteOutcome.Failed(failureLine(e))
    }
