// EXPERIENCE.md §7.4 — owner decision 2026-10-05: location sharing waits for
// the server. A change is saved only when the server took it; otherwise
// nothing moves and the line says why ("Couldn't save that." + §4).
package org.nuruplace.member.feature.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LocationSharingTest {
    private val here = CoarseFix(-1.29, 36.82)
    private fun line(e: Throwable) = "Couldn't save that. ${e.message}"

    @Test
    fun `turning on sends one fix, and is saved only once the server took it`() = runBlocking {
        var sent: CoarseFix? = null
        val r = LocationSharing.change(true, fix = { here }, share = { sent = it }, stop = { error("not this") }, failureLine = ::line)
        assertEquals(LocationShareResult.Saved(true), r)
        assertEquals(here, sent)
    }

    @Test
    fun `a failed share leaves sharing as it was and says why`() = runBlocking {
        val r = LocationSharing.change(true, fix = { here }, share = { throw IOException("You're offline") }, stop = {}, failureLine = ::line)
        assertEquals(LocationShareResult.Failed("Couldn't save that. You're offline"), r)
    }

    @Test
    fun `no position from the phone sends nothing and says so`() = runBlocking {
        var called = false
        val r = LocationSharing.change(true, fix = { null }, share = { called = true }, stop = {}, failureLine = ::line)
        assertEquals(LocationShareResult.Failed(LocationSharing.NO_FIX_LINE), r)
        assertFalse(called)
        assertTrue(LocationSharing.NO_FIX_LINE.startsWith("Couldn't save that."))
    }

    @Test
    fun `turning off is saved only once the server stopped — never Off while it still holds the area`() = runBlocking {
        var stopped = false
        assertEquals(LocationShareResult.Saved(false), LocationSharing.change(false, fix = { here }, share = {}, stop = { stopped = true }, failureLine = ::line))
        assertTrue(stopped)
        val failed = LocationSharing.change(false, fix = { here }, share = {}, stop = { throw IOException("Something went wrong on our side") }, failureLine = ::line)
        assertEquals(LocationShareResult.Failed("Couldn't save that. Something went wrong on our side"), failed)
    }

    @Test(expected = CancellationException::class)
    fun `a cancelled change is not a failure line`(): Unit = runBlocking {
        LocationSharing.change(true, fix = { here }, share = { throw CancellationException("left the screen") }, stop = {}, failureLine = ::line)
    }
}
