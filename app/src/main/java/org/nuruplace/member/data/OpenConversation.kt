// The chat thread on screen right now, if any. ChatThreadScreen claims it
// while it is resumed and gives it up when it pauses or leaves, so the id is
// only ever set while the member is actually looking at that thread in the
// foreground. NuruMessagingService reads it: a message push for THAT thread
// posts no notification — it arrives here instead, and the open thread
// refreshes (the thread has no live feed of its own) with a light tick
// (owner request 2026-09-28; landsInOpenThread holds the rule).
package org.nuruplace.member.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object OpenConversation {
    /** A message that landed in the open thread; [buzz] is false when the
     *  member turned Sound and vibration off (the push's `nuru_sound`). */
    data class Arrival(val conversationId: String, val buzz: Boolean)

    @Volatile
    var id: String? = null
        private set

    private val _arrivals = MutableSharedFlow<Arrival>(extraBufferCapacity = 16)
    val arrivals: SharedFlow<Arrival> = _arrivals.asSharedFlow()

    /** Both claims run on the main thread (lifecycle effects); the FCM
     *  service only ever reads [id], hence @Volatile and no lock. */
    fun opened(conversationId: String) { id = conversationId }

    /** Clears only if [conversationId] still holds it — the next thread may
     *  already have claimed it (its resume can run before this one's pause
     *  is delivered). */
    fun closed(conversationId: String) { if (id == conversationId) id = null }

    /** Called from the FCM service's thread; never blocks, never throws. */
    fun arrived(conversationId: String, buzz: Boolean) { _arrivals.tryEmit(Arrival(conversationId, buzz)) }
}
