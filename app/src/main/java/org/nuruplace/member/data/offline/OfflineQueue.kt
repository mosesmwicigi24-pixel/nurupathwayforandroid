// The offline mutation queue + its drain engine (§1.7). enqueue() records a write
// as the local system of record with a stable idempotency key; the engine replays
// the queue in seq order via POST /sync/push whenever the device is online — on
// app start, on connectivity regained, and right after an enqueue. Server results
// are authoritative: applied/duplicate rows are removed; a rejected row is a
// permanent server refusal (bad payload, offline-forbidden) so it is dropped
// loudly rather than retried forever. Money is never enqueued (§5.6).
package org.nuruplace.member.data.offline

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.nuruplace.member.data.net.MemberApi
import org.nuruplace.member.data.net.SyncMutation
import org.nuruplace.member.data.net.SyncPushBody

class OfflineQueue(
    private val dao: MutationDao,
    private val connectivity: NetworkStatus,
    private val apiProvider: () -> MemberApi,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainLock = Mutex()

    private val _pending = MutableStateFlow(0)
    /** Live count of queued mutations — for an "N pending" UI cue. */
    val pending: StateFlow<Int> = _pending

    /** Watch connectivity and drain the moment we come back online. */
    fun start() {
        scope.launch { refreshCount() }
        scope.launch {
            connectivity.online().collect { online -> if (online) drain() }
        }
    }

    /** Record an offline-originated write, then try to flush immediately. */
    suspend fun enqueue(domain: String, op: String, payload: JsonObject) {
        val seq = dao.maxSeq() + 1
        dao.insert(
            PendingMutation(
                mutationId = payload.mutationIdOrNew(),
                seq = seq,
                domain = domain,
                op = op,
                payloadJson = json.encodeToString(JsonObject.serializer(), payload),
                createdAt = nowMillis(),
            ),
        )
        refreshCount()
        drain()
    }

    /** Flush the whole queue in seq order. No-op when offline or already draining. */
    suspend fun drain() {
        if (!connectivity.isOnline()) return
        if (!drainLock.tryLock()) return
        try {
            while (connectivity.isOnline()) {
                val batch = dao.oldest(BATCH)
                if (batch.isEmpty()) break
                val body = SyncPushBody(
                    mutations = batch.map {
                        SyncMutation(
                            mutationId = it.mutationId,
                            seq = it.seq,
                            domain = it.domain,
                            op = it.op,
                            payload = json.decodeFromString(JsonObject.serializer(), it.payloadJson),
                        )
                    },
                )
                val result = try {
                    apiProvider().syncPush(body)
                } catch (e: Exception) {
                    // Transport/auth failure — leave the queue intact for the next
                    // connectivity event; do not drop anything.
                    Log.w(TAG, "sync push failed, will retry: ${e.message}")
                    break
                }
                for (r in result.results) {
                    when (r.status) {
                        "applied", "duplicate" -> dao.deleteById(r.mutationId)
                        else -> {
                            Log.w(TAG, "mutation ${r.mutationId} rejected: ${r.code} ${r.detail}")
                            dao.deleteById(r.mutationId)   // permanent refusal — drop, don't loop
                        }
                    }
                }
                refreshCount()
                if (batch.size < BATCH) break
            }
        } finally {
            drainLock.unlock()
        }
    }

    private suspend fun refreshCount() {
        _pending.value = dao.count()
    }

    private fun JsonObject.mutationIdOrNew(): String =
        (this["client_mutation_id"] ?: this["clientMutationId"])?.let {
            it.toString().trim('"').ifBlank { newUuid() }
        } ?: newUuid()

    private companion object {
        const val BATCH = 100
        const val TAG = "OfflineQueue"
    }
}

// Extracted so tests can substitute a deterministic clock/uuid if needed.
internal fun nowMillis(): Long = System.currentTimeMillis()
internal fun newUuid(): String = java.util.UUID.randomUUID().toString()
