// The last-good-copy cache (ApiClient) keeps a good read on disk so a stale
// page can stand in while the server is away — but never a response the
// server marked no-store: the giving receipts and statements are private
// documents, sent `Cache-Control: private, no-store` since pathway Giving
// Cycle 6, and rewriting that header kept them on disk anyway.
package org.nuruplace.member.data.net

import okhttp3.CacheControl
import okhttp3.Headers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastGoodCopyTest {
    @Test
    fun `a receipt or statement the server marks no-store is never kept`() {
        // What the server sends on /giving/statement.pdf, …/receipt.pdf, /giving/partners/statement.pdf.
        val pdf = CacheControl.parse(Headers.headersOf("Cache-Control", "private, no-store"))
        assertTrue(pdf.noStore)
        assertFalse(storableLastGoodCopy(successful = true, noStore = pdf.noStore))
    }

    @Test
    fun `every other good read is kept as before, and a failure never is`() {
        val plain = CacheControl.parse(Headers.headersOf())
        assertTrue(storableLastGoodCopy(successful = true, noStore = plain.noStore))
        val curriculum = CacheControl.parse(Headers.headersOf("Cache-Control", "public, max-age=86400"))
        assertTrue(storableLastGoodCopy(successful = true, noStore = curriculum.noStore))
        assertFalse(storableLastGoodCopy(successful = false, noStore = false))
    }
}
