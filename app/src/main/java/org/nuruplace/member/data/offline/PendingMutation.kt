// The client mutation queue is the mobile system of record for offline-originated
// writes (§1.7). Each row is one queued write — a stable mutationId (the
// idempotency key the server dedupes on, §2.1/§3.6), its domain:op, the JSON
// payload, and a monotonic seq so the server replays them in origination order.
// Money is NEVER enqueued here (§5.6).
package org.nuruplace.member.data.offline

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "pending_mutations")
data class PendingMutation(
    @PrimaryKey val mutationId: String,   // UUID — the server idempotency key
    val seq: Long,                        // origination order (monotonic, per-device)
    val domain: String,                   // e.g. "prayer_entries"
    val op: String,                       // e.g. "upsert"
    val payloadJson: String,              // serialized payload object
    val createdAt: Long,                  // epoch millis
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Dao
interface MutationDao {
    @Insert
    suspend fun insert(mutation: PendingMutation)

    @Query("SELECT * FROM pending_mutations ORDER BY seq ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingMutation>

    @Query("SELECT COUNT(*) FROM pending_mutations")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(seq), 0) FROM pending_mutations")
    suspend fun maxSeq(): Long

    @Query("DELETE FROM pending_mutations WHERE mutationId = :mutationId")
    suspend fun deleteById(mutationId: String)

    @Query("UPDATE pending_mutations SET attempts = attempts + 1, lastError = :error WHERE mutationId = :mutationId")
    suspend fun markFailed(mutationId: String, error: String?)
}
