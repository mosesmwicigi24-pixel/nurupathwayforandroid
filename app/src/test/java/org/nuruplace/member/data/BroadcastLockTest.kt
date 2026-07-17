package org.nuruplace.member.data

import org.junit.Assert.assertFalse
import org.junit.Test

/** BroadcastLock.isEnrolled only reads AppPrefs (no Keystore/biometric touch),
 *  so it must stay a safe, non-crashing false before AppPrefs.init(context)
 *  has ever run — e.g. a very first cold start racing the Broadcast tab. */
class BroadcastLockTest {
    @Test fun `not enrolled before AppPrefs is initialized`() {
        assertFalse(BroadcastLock.isEnrolled)
    }
}
