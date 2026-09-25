// Receipt v2 (owner 2026-09-25): the receipt reads the SERVER's display
// names and never guesses — pinned here together with the fallbacks an older
// server (no fund_name / pledge / need / method_label / member_name) still needs.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.GivingDetail
import org.nuruplace.member.data.net.IntentPledge
import org.nuruplace.member.data.net.ReceiptNeed

class GiveReceiptCopyTest {
    private val base = GivingDetail(
        transactionId = "3f2a9c1e-7b1d-4c2a-9e0f-123456789abc",
        amountMinor = 50_000,
        currency = "KES",
        status = "succeeded",
        fund = "discipleship",
        method = "mpesa",
        receiptCode = "UIPJ27PBO3",
        createdAt = "2026-09-25T16:40:00Z",
        settledAt = "2026-09-25T17:11:00Z",
        fundName = "Discipleship",
        methodLabel = "M-Pesa",
        memberName = "Moses Mwicigi",
    )
    private val pledge = IntentPledge("p1", "School fees")
    private val need = ReceiptNeed("n1", "Sound desk")

    @Test
    fun `destination is the pledge, then the need, then the fund by server name, then by code`() {
        assertEquals("toward your School fees pledge", receiptDestinationLine(base.copy(pledge = pledge)))
        assertEquals("to Sound desk", receiptDestinationLine(base.copy(need = need)))
        // A pledge outranks a need when both ride along.
        assertEquals("toward your School fees pledge", receiptDestinationLine(base.copy(pledge = pledge, need = need)))
        assertEquals("to the Discipleship fund", receiptDestinationLine(base))
        // Older server: no fund_name → the local table's name for the code.
        assertEquals("to the Discipleship fund", receiptDestinationLine(base.copy(fundName = null)))
        assertEquals("to the Tithe fund", receiptDestinationLine(base.copy(fundName = "  ", fund = "tithe")))
        // A code the table does not know → the code, capitalised.
        assertEquals("to the Building fund", receiptDestinationLine(base.copy(fundName = null, fund = "building")))
        // A blank pledge title is no pledge; a blank need title is no need.
        assertEquals("to the Discipleship fund", receiptDestinationLine(base.copy(pledge = IntentPledge("p1", " "))))
        assertEquals("to the Discipleship fund", receiptDestinationLine(base.copy(need = ReceiptNeed("n1", ""))))
    }

    @Test
    fun `share text is amount, destination, method with reference, day`() {
        assertEquals("KSh 500 to the Discipleship fund · M-Pesa UIPJ27PBO3 · 25 Sep 2026", receiptShareText(base))
        assertEquals(
            "KSh 500 toward your School fees pledge · M-Pesa UIPJ27PBO3 · 25 Sep 2026",
            receiptShareText(base.copy(pledge = pledge)),
        )
        // No receipt code → the provider ref; neither → the method alone.
        assertEquals(
            "KSh 500 to the Discipleship fund · M-Pesa ABC123 · 25 Sep 2026",
            receiptShareText(base.copy(receiptCode = null, providerRef = "ABC123")),
        )
        assertEquals(
            "KSh 500 to the Discipleship fund · M-Pesa · 25 Sep 2026",
            receiptShareText(base.copy(receiptCode = null, providerRef = null)),
        )
        // Older server: no method_label → the local label; USD keeps its symbol.
        assertEquals(
            "$12.50 to the Discipleship fund · PayPal 9AB · 25 Sep 2026",
            receiptShareText(base.copy(methodLabel = null, method = "paypal", currency = "USD", amountMinor = 1_250, receiptCode = null, providerRef = "9AB")),
        )
        // No method at all and no dates → nothing dangling.
        assertEquals(
            "KSh 500 to the Discipleship fund",
            receiptShareText(base.copy(methodLabel = null, method = null, receiptCode = null, providerRef = null, createdAt = "", settledAt = null)),
        )
    }

    @Test
    fun `receipt is dated by settled_at, else created_at, in Nairobi`() {
        assertEquals("Fri 25 Sep 2026 · 8:11 PM", receiptWhen(base))
        assertEquals("Fri 25 Sep 2026 · 7:40 PM", receiptWhen(base.copy(settledAt = null)))
        assertEquals("Fri 25 Sep 2026 · 7:40 PM", receiptWhen(base.copy(settledAt = "")))
        assertEquals("—", receiptWhen(base.copy(settledAt = null, createdAt = "")))
        assertEquals("25 Sep 2026", receiptDay(base))
        assertEquals("", receiptDay(base.copy(settledAt = null, createdAt = "not a date")))
    }

    @Test
    fun `status maps to a chip only when the gift has not succeeded`() {
        assertNull(receiptStatusChip(base))
        assertNull(receiptStatusChip(base.copy(status = "settled")))
        assertNull(receiptStatusChip(base.copy(status = "COMPLETED")))
        assertEquals(ReceiptChip("Waiting for M-Pesa", ReceiptTone.Waiting), receiptStatusChip(base.copy(status = "pending")))
        assertEquals(
            ReceiptChip("Waiting for Airtel Money", ReceiptTone.Waiting),
            receiptStatusChip(base.copy(status = "processing", method = "airtel", methodLabel = null)),
        )
        // Card / PayPal have no PIN prompt to wait on.
        assertEquals(ReceiptChip("Processing", ReceiptTone.Waiting), receiptStatusChip(base.copy(status = "pending", method = "card", methodLabel = "Card")))
        // An unknown status is still pending — never shown as received.
        assertEquals(ReceiptTone.Waiting, receiptStatusChip(base.copy(status = "weird"))?.tone)
        assertEquals(ReceiptChip("Not completed", ReceiptTone.NotCompleted), receiptStatusChip(base.copy(status = "failed")))
        assertEquals(ReceiptChip("Not completed", ReceiptTone.NotCompleted), receiptStatusChip(base.copy(status = "cancelled")))
        assertEquals(ReceiptChip("Refunded", ReceiptTone.Refunded), receiptStatusChip(base.copy(status = "refunded")))
    }

    @Test
    fun `eyebrow follows the chip`() {
        assertEquals("GIFT RECEIVED", receiptEyebrow(null))
        assertEquals("GIFT PENDING", receiptEyebrow(ReceiptChip("Waiting for M-Pesa", ReceiptTone.Waiting)))
        assertEquals("GIFT NOT COMPLETED", receiptEyebrow(ReceiptChip("Not completed", ReceiptTone.NotCompleted)))
        assertEquals("GIFT REFUNDED", receiptEyebrow(ReceiptChip("Refunded", ReceiptTone.Refunded)))
    }

    @Test
    fun `first name is the server's member_name, else the profile's, else nothing`() {
        assertEquals("Moses", receiptFirstName("Moses Mwicigi", "Someone Else"))
        assertEquals("Grace", receiptFirstName(null, "  Grace   Wanjiru "))
        assertEquals("Grace", receiptFirstName("   ", "Grace"))
        assertNull(receiptFirstName(null, null))
        assertNull(receiptFirstName("", "  "))
    }

    @Test
    fun `method and reference labels`() {
        assertEquals("M-Pesa", receiptMethodLabel(base))
        assertEquals("Airtel Money", receiptMethodLabel(base.copy(methodLabel = null, method = "airtel")))
        assertEquals("Manual", receiptMethodLabel(base.copy(methodLabel = "Manual", method = null)))
        assertEquals("—", receiptMethodLabel(base.copy(methodLabel = null, method = null)))
        assertEquals("M-Pesa receipt", receiptReferenceLabel(base))
        assertEquals("Airtel receipt", receiptReferenceLabel(base.copy(methodLabel = "Airtel Money", method = "airtel")))
        assertEquals("Reference", receiptReferenceLabel(base.copy(methodLabel = "Card", method = "card")))
        assertEquals("Reference", receiptReferenceLabel(base.copy(methodLabel = null, method = null)))
        assertEquals("UIPJ27PBO3", receiptProviderRef(base))
        assertEquals("ord_1", receiptProviderRef(base.copy(receiptCode = " ", providerRef = "ord_1")))
        assertNull(receiptProviderRef(base.copy(receiptCode = null, providerRef = null)))
    }

    @Test
    fun `where it went names the fund and, on a pledge, says so`() {
        assertEquals("100% of this gift reaches the Discipleship fund.", receiptWhereItWent(base))
        assertEquals(
            "100% of this gift reaches the Discipleship fund. · counts toward your pledge",
            receiptWhereItWent(base.copy(pledge = pledge)),
        )
        assertEquals("100% of this gift reaches the Tithe fund.", receiptWhereItWent(base.copy(fundName = null, fund = "tithe")))
    }

    @Test
    fun `amount splits into a currency mark and a number`() {
        assertEquals("KSh" to "500", receiptAmountParts(50_000, "KES"))
        assertEquals("KSh" to "1,000", receiptAmountParts(100_000, null))
        assertEquals("$" to "12.50", receiptAmountParts(1_250, "USD"))
    }

    @Test
    fun `pdf file name is the receipt code, else the short id, filesystem-safe`() {
        assertEquals("nuru-receipt-UIPJ27PBO3.pdf", receiptFileName(base))
        assertEquals("nuru-receipt-3f2a9c1e.pdf", receiptFileName(base.copy(receiptCode = null)))
        assertEquals("nuru-receipt-AB12.pdf", receiptFileName(base.copy(receiptCode = "AB/12 ../")))
        assertEquals("nuru-receipt-UIP_J27-PBO3.pdf", receiptFileName(base.copy(receiptCode = "UIP_J27-PBO3")))
        assertEquals("nuru-receipt-gift.pdf", receiptFileName(base.copy(receiptCode = "///", transactionId = "")))
        assertEquals("3f2a9c1e…", receiptShortId(base))
    }
}

class MoneyFormatTest {
    @org.junit.Test
    fun `money prints the ISO code for currencies other than KES and USD, never a template`() {
        org.junit.Assert.assertEquals("KSh 1,000", money(100_000, "KES"))
        org.junit.Assert.assertEquals("$1,000.00", money(100_000, "USD"))
        org.junit.Assert.assertEquals("EUR 12.50", money(1_250, "eur"))
        org.junit.Assert.assertFalse(money(1_250, "eur").contains("{"))
    }
}
