// Drain-engine behaviour (§1.7, §3.6): the queue replays in seq order, removes
// applied/duplicate rows, drops server-rejected rows (no infinite retry), and —
// critically — keeps everything intact when the push fails at the transport
// layer so nothing is lost while offline.
package org.nuruplace.member.data.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.MemberApi
import org.nuruplace.member.data.net.SyncPushBody
import org.nuruplace.member.data.net.SyncPushResult
import org.nuruplace.member.data.net.SyncMutationResult
import java.io.IOException
import java.lang.reflect.Proxy

private class FakeDao : MutationDao {
    val rows = mutableListOf<PendingMutation>()
    override suspend fun insert(mutation: PendingMutation) { rows.add(mutation) }
    override suspend fun oldest(limit: Int): List<PendingMutation> = rows.sortedBy { it.seq }.take(limit)
    override suspend fun count(): Int = rows.size
    override suspend fun maxSeq(): Long = rows.maxOfOrNull { it.seq } ?: 0
    override suspend fun deleteById(mutationId: String) { rows.removeAll { it.mutationId == mutationId } }
    override suspend fun markFailed(mutationId: String, error: String?) {}
}

private class FakeNet(private val online: Boolean) : NetworkStatus {
    override fun isOnline() = online
    override fun online(): Flow<Boolean> = flowOf(online)
}

/** A MemberApi whose only implemented method is syncPush; the rest (unused here)
 *  are dynamic-proxy stubs that throw if ever called. */
private val unsupportedApi: MemberApi = Proxy.newProxyInstance(
    MemberApi::class.java.classLoader,
    arrayOf(MemberApi::class.java),
) { _, method, _ -> throw UnsupportedOperationException(method.name) } as MemberApi

private class FakeApi(
    val onPush: (SyncPushBody) -> SyncPushResult,
) : MemberApi by unsupportedApi {
    var pushes = 0
    override suspend fun syncPush(body: SyncPushBody): SyncPushResult {
        pushes++
        return onPush(body)
    }
}

private fun queue(dao: MutationDao, net: NetworkStatus, api: MemberApi) =
    OfflineQueue(dao, net, { api }, Json { ignoreUnknownKeys = true })

private fun row(seq: Long, id: String = "m$seq") =
    PendingMutation(mutationId = id, seq = seq, domain = "prayer_entries", op = "upsert", payloadJson = "{}", createdAt = seq)

class OfflineQueueTest {
    @Test fun `applied and duplicate rows are removed`() = runTest {
        val dao = FakeDao().apply { rows += listOf(row(1, "a"), row(2, "b")) }
        val api = FakeApi { body ->
            SyncPushResult(body.mutations.map {
                SyncMutationResult(it.mutationId, if (it.mutationId == "a") "applied" else "duplicate")
            })
        }
        queue(dao, FakeNet(true), api).drain()
        assertEquals(0, dao.rows.size)
    }

    @Test fun `rejected rows are dropped, not retried forever`() = runTest {
        val dao = FakeDao().apply { rows += row(1, "bad") }
        val api = FakeApi { body -> SyncPushResult(body.mutations.map { SyncMutationResult(it.mutationId, "rejected", "VALIDATION_FAILED") }) }
        queue(dao, FakeNet(true), api).drain()
        assertEquals(0, dao.rows.size)
        assertEquals(1, api.pushes)   // one attempt, then gone
    }

    @Test fun `transport failure keeps the queue intact`() = runTest {
        val dao = FakeDao().apply { rows += listOf(row(1), row(2)) }
        val api = FakeApi { throw IOException("offline") }
        queue(dao, FakeNet(true), api).drain()
        assertEquals(2, dao.rows.size)   // nothing lost
    }

    @Test fun `offline drain is a no-op`() = runTest {
        val dao = FakeDao().apply { rows += row(1) }
        val api = FakeApi { SyncPushResult(emptyList()) }
        queue(dao, FakeNet(false), api).drain()
        assertEquals(1, dao.rows.size)
        assertEquals(0, api.pushes)
    }

    @Test fun `mutations replay in seq order`() = runTest {
        val dao = FakeDao().apply { rows += listOf(row(3, "c"), row(1, "a"), row(2, "b")) }
        var seen = listOf<Long>()
        val api = FakeApi { body ->
            seen = body.mutations.map { it.seq }
            SyncPushResult(body.mutations.map { SyncMutationResult(it.mutationId, "applied") })
        }
        queue(dao, FakeNet(true), api).drain()
        assertEquals(listOf(1L, 2L, 3L), seen)
        assertTrue(dao.rows.isEmpty())
    }

    // ── WS-2 characterization: the idempotency-critical paths ──────────────

    /** The mutation id IS the dedup key (§3.6). Extraction must strip JSON
     *  quotes, accept both key spellings, and mint a uuid when absent/blank —
     *  a wrong id here silently breaks replay dedup on the server. */
    @Test fun `enqueue derives the idempotency key from either spelling, minting one when absent`() = runTest {
        val dao = FakeDao()
        val q = queue(dao, FakeNet(online = false), FakeApi { SyncPushResult(emptyList()) })
        val j = Json { ignoreUnknownKeys = true }

        q.enqueue("prayer_entries", "upsert", j.parseToJsonElement("""{"client_mutation_id":"aaa-1"}""") as JsonObject)
        q.enqueue("prayer_entries", "upsert", j.parseToJsonElement("""{"clientMutationId":"bbb-2"}""") as JsonObject)
        q.enqueue("prayer_entries", "upsert", j.parseToJsonElement("""{"client_mutation_id":""}""") as JsonObject)
        q.enqueue("prayer_entries", "upsert", j.parseToJsonElement("""{"body":"no id at all"}""") as JsonObject)

        assertEquals("aaa-1", dao.rows[0].mutationId)          // quotes stripped, not "\"aaa-1\""
        assertEquals("bbb-2", dao.rows[1].mutationId)          // camelCase spelling honored
        assertTrue(dao.rows[2].mutationId.isNotBlank())        // blank → minted
        assertTrue(dao.rows[3].mutationId.isNotBlank())        // absent → minted
        assertEquals(4, dao.rows.map { it.mutationId }.toSet().size) // all distinct
    }

    /** enqueue() assigns strictly increasing seq — replay order is meaningless
     *  if two writes can share a position. */
    @Test fun `enqueue assigns strictly increasing seq`() = runTest {
        val dao = FakeDao()
        val q = queue(dao, FakeNet(online = false), FakeApi { SyncPushResult(emptyList()) })
        val j = Json { ignoreUnknownKeys = true }
        repeat(3) { q.enqueue("prayer_entries", "upsert", j.parseToJsonElement("{}") as JsonObject) }
        assertEquals(listOf(1L, 2L, 3L), dao.rows.map { it.seq })
    }

    /** More rows than one batch: the drain loops until the queue is empty
     *  rather than stopping after the first page. */
    @Test fun `drain pages through more than one batch`() = runTest {
        val dao = FakeDao()
        repeat(150) { i -> dao.rows.add(row(seq = (i + 1).toLong())) }
        val api = FakeApi { body ->
            SyncPushResult(body.mutations.map { SyncMutationResult(it.mutationId, "applied", null, null) })
        }
        queue(dao, FakeNet(online = true), api).drain()
        assertEquals(0, dao.rows.size)      // everything flushed
        assertEquals(2, api.pushes)         // 100 + 50 — two pages, not one
    }
}
