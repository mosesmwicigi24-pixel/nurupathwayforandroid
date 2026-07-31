package org.nuruplace.member.feature.live

import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Coverage for the two production-proven Nuru Live bugs fixed alongside this
 * test (see LiveWebRtc.kt's header docs for the full incident writeups):
 *
 *  - Bug 2 (guests can never authenticate): MediaMTX v1.19.3 ignores
 *    `user`/`pass` query params on WHIP/WHEP and only honours HTTP Basic
 *    auth — proven against production. [basicAuthHeader]/[buildSdpOfferRequest]/
 *    [buildDeleteRequest] are pure functions specifically so this is
 *    testable without a real MediaMTX server or a network stack.
 *
 *  - Bug 1 (the host crashes when WebRTC first loads): a disassembly-proven
 *    native RTC_CHECK trap the app can never recover from once triggered.
 *    [OneShotInit] is the structural guarantee that the risky native init
 *    call runs at most once per process no matter how many callers
 *    (WhipPublisher on a guest device, WhepSubscriber on the host) race it
 *    concurrently — verified here under real thread contention.
 */
class LiveWebRtcTest {
    // ── basicAuthHeader — Bug 2 ─────────────────────────────────────────────

    @Test fun `basicAuthHeader base64-encodes user colon pass`() {
        val header = LiveWebRtc.basicAuthHeader("alice", "secret-token")
        assertTrue(header.startsWith("Basic "))
        val decoded = String(Base64.getDecoder().decode(header.removePrefix("Basic ")), Charsets.UTF_8)
        assertEquals("alice:secret-token", decoded)
    }

    @Test fun `basicAuthHeader pairs user and pass in the correct order`() {
        // A swapped pairing would still "look like" valid Basic auth (base64
        // of SOME "x:y" string) but authenticate as the wrong identity —
        // the exact failure mode that made bug 2 so easy to miss.
        val header = LiveWebRtc.basicAuthHeader("streamId123", "streamKey456")
        val decoded = String(Base64.getDecoder().decode(header.removePrefix("Basic ")), Charsets.UTF_8)
        val (user, pass) = decoded.split(":", limit = 2)
        assertEquals("streamId123", user)
        assertEquals("streamKey456", pass)
    }

    @Test fun `basicAuthHeader round-trips credentials containing colons and special chars`() {
        // Stream keys/tokens are opaque server-minted strings — Basic auth's
        // own "first colon splits user from pass" rule means a colon INSIDE
        // the password is fine (still round-trips) but one inside the
        // username would corrupt the pairing; this app's usernames are
        // userIds/streamIds we mint (no colons), and the guest brief calls
        // for auth-header correctness, not defending against a malformed id.
        val header = LiveWebRtc.basicAuthHeader("guest-42", "tok:with:colons/+=")
        val decoded = String(Base64.getDecoder().decode(header.removePrefix("Basic ")), Charsets.UTF_8)
        assertEquals("guest-42:tok:with:colons/+=", decoded)
        assertEquals("guest-42", decoded.substringBefore(":"))
        assertEquals("tok:with:colons/+=", decoded.substringAfter(":"))
    }

    // ── buildSdpOfferRequest / buildDeleteRequest — no query-param leakage ──

    @Test fun `buildSdpOfferRequest carries auth as a header, never in the URL`() {
        val request = LiveWebRtc.buildSdpOfferRequest(
            url = "https://live.nuruplace.org/whip/abc123",
            offerSdp = "v=0\r\n...",
            user = "abc123",
            pass = "top-secret-key",
        )
        assertNull("credentials must never appear in the request URL's query string", request.url.query)
        assertEquals("https://live.nuruplace.org/whip/abc123", request.url.toString())
        assertEquals(LiveWebRtc.basicAuthHeader("abc123", "top-secret-key"), request.header("Authorization"))
        assertEquals("POST", request.method)
        assertFalse(
            "the URL string itself must not contain the raw credential",
            request.url.toString().contains("top-secret-key"),
        )
    }

    @Test fun `buildDeleteRequest also carries auth as a header for teardown`() {
        // MediaMTX's session-resource DELETE is authenticated the same way
        // as the initial POST; the pre-fix code sent this DELETE with no
        // auth at all (the Location header never carried the query-param
        // credentials either), which is why this needed its own coverage.
        val request = LiveWebRtc.buildDeleteRequest(
            resourceUrl = "https://live.nuruplace.org/whip/abc123/def456",
            user = "abc123",
            pass = "top-secret-key",
        )
        assertNull(request.url.query)
        assertEquals(LiveWebRtc.basicAuthHeader("abc123", "top-secret-key"), request.header("Authorization"))
        assertEquals("DELETE", request.method)
    }

    @Test fun `different credentials produce different auth headers`() {
        val a = LiveWebRtc.buildSdpOfferRequest("https://x/whip", "sdp", "user1", "pass1")
        val b = LiveWebRtc.buildSdpOfferRequest("https://x/whip", "sdp", "user2", "pass2")
        assertTrue(a.header("Authorization") != b.header("Authorization"))
    }

    // ── OneShotInit — Bug 1's structural guard ──────────────────────────────

    @Test fun `OneShotInit runs init exactly once and caches the value`() {
        val calls = AtomicInteger(0)
        val guard = OneShotInit<String>()
        val first = guard.getOrInit { calls.incrementAndGet(); "factory" }
        val second = guard.getOrInit { calls.incrementAndGet(); "factory" }
        assertEquals(1, calls.get())
        assertSame(first, second)
        assertEquals("factory", guard.value())
        assertNull(guard.failure())
    }

    @Test fun `OneShotInit caches a failure and never retries the risky call`() {
        // This is the exact shape of the real bug: a native init that has
        // already proven risky (or already failed) must not be re-entered
        // by a second caller — WhipPublisher (a guest) and WhepSubscriber
        // (the host) both call LiveWebRtc.factory(), and after one process-
        // wide attempt, EVERY subsequent caller — success or failure — must
        // observe the SAME outcome without ever invoking init again.
        val calls = AtomicInteger(0)
        val guard = OneShotInit<String>()
        val boom = IllegalStateException("native init failed")
        val firstError = try {
            guard.getOrInit { calls.incrementAndGet(); throw boom }
        } catch (e: IllegalStateException) {
            e
        }
        val secondError = try {
            guard.getOrInit { calls.incrementAndGet(); "should never run" }
        } catch (e: IllegalStateException) {
            e
        }
        assertEquals(1, calls.get())
        assertSame(boom, firstError)
        assertSame(boom, secondError)
        assertSame(boom, guard.failure())
        assertNull(guard.value())
    }

    @Test fun `OneShotInit under concurrent callers invokes init exactly once`() {
        // Simulates the real race: a guest publish and a host subscribe (and
        // potentially several guests) all reaching LiveWebRtc.factory() for
        // the first time within the same window. Every thread must see the
        // identical outcome and init must run exactly once — this is the
        // property that makes "the second/failing call never happens" a
        // guarantee rather than a hope.
        val threadCount = 50
        val calls = AtomicInteger(0)
        val guard = OneShotInit<Int>()
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val pool = Executors.newFixedThreadPool(threadCount)
        try {
            repeat(threadCount) {
                pool.execute {
                    readyLatch.countDown()
                    startLatch.await()
                    try {
                        val value = guard.getOrInit {
                            // A slow, "risky" init — sleeping here maximizes
                            // the window for a bad implementation to let two
                            // threads both start it.
                            Thread.sleep(20)
                            calls.incrementAndGet()
                            42
                        }
                        results.add(value)
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
            assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
            startLatch.countDown()
            pool.shutdown()
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) fail("threads did not finish in time")
        } finally {
            pool.shutdownNow()
        }
        assertTrue("init lambda must run at most once, ran ${calls.get()} times", calls.get() == 1)
        assertTrue(errors.isEmpty())
        assertEquals(threadCount, results.size)
        assertTrue("every caller must observe the same cached value", results.all { it == 42 })
    }
}
