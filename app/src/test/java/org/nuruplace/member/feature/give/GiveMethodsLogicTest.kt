// Giving Cycle 1 — HOW a gift is paid. The method list is the server's word
// (GET /giving/methods) narrowed to what this app can carry: a card (no Stripe
// SDK) and PayPal's dollars (a shilling form) are never selectable, a rail
// the server switched off never is, and no answer at all leaves M-Pesa alone.
// The prompt number follows the server's own Kenyan-mobile rule exactly.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.GivingMethodsRes

@OptIn(ExperimentalSerializationApi::class)
class GiveMethodsLogicTest {
    // Same configuration as ApiClient's private json.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    /** What the server sends today (financial/service.ts listMethods). */
    private val live = """{
        "methods":[
          {"key":"mpesa","label":"M-Pesa","enabled":true,"unavailable_reason":null,"currency":"KES","min_minor":100,"max_minor":25000000,"whole_units":true,"recurring":true,"needs_phone":true},
          {"key":"airtel","label":"Airtel Money","enabled":false,"unavailable_reason":"coming_soon","currency":"KES","min_minor":100,"max_minor":15000000,"whole_units":true,"recurring":false,"needs_phone":true},
          {"key":"paypal","label":"PayPal","enabled":true,"unavailable_reason":null,"currency":"USD","min_minor":100,"max_minor":1000000,"whole_units":false,"recurring":false,"needs_phone":false},
          {"key":"card","label":"Card","enabled":false,"unavailable_reason":"coming_soon","currency":null,"min_minor":100,"max_minor":100000000,"whole_units":false,"recurring":false,"needs_phone":false}
        ],
        "phone_on_file":"+254711222333",
        "default_method":"mpesa"
    }"""

    @Test
    fun `the methods answer decodes enabled, disabled and null fields`() {
        val res = json.decodeFromString<GivingMethodsRes>(live)
        assertEquals(listOf("mpesa", "airtel", "paypal", "card"), res.methods.map { it.key })
        val mpesa = res.methods[0]
        assertTrue(mpesa.enabled)
        assertNull(mpesa.unavailableReason)
        assertEquals("KES", mpesa.currency)
        assertEquals(100L, mpesa.minMinor)
        assertEquals(25_000_000L, mpesa.maxMinor)
        assertTrue(mpesa.wholeUnits)
        assertTrue(mpesa.recurring)
        assertTrue(mpesa.needsPhone)
        val airtel = res.methods[1]
        assertFalse(airtel.enabled)
        assertEquals("coming_soon", airtel.unavailableReason)
        assertFalse(airtel.recurring)
        assertNull(res.methods[3].currency) // card: any currency
        assertEquals("+254711222333", res.phoneOnFile)
        assertEquals("mpesa", res.defaultMethod)

        // Nothing on file, nothing enabled: both nulls decode.
        val bare = json.decodeFromString<GivingMethodsRes>("""{"methods":[],"phone_on_file":null,"default_method":null}""")
        assertNull(bare.phoneOnFile)
        assertNull(bare.defaultMethod)
        assertTrue(bare.methods.isEmpty())
    }

    @Test
    fun `unknown keys are ignored, and a row that does not say enabled is not`() {
        val res = json.decodeFromString<GivingMethodsRes>(
            """{"methods":[
                 {"key":"mpesa","label":"M-Pesa","enabled":true,"fee_hint":"free","promo":{"x":1},"currency":"KES","min_minor":100,"max_minor":25000000,"recurring":true,"needs_phone":true},
                 {"key":"equity","label":"Equity","enabled":true,"currency":"KES","min_minor":100,"max_minor":100,"recurring":true,"needs_phone":false},
                 {"key":"airtel","label":"Airtel Money"}
               ],
               "phone_on_file":"+254711222333","default_method":"mpesa","region":"KE"}""",
        )
        assertEquals(3, res.methods.size)
        val airtel = res.methods[2]
        assertFalse(airtel.enabled) // absent → not enabled
        assertFalse(airtel.recurring)
        assertFalse(airtel.needsPhone)
        // A rail this app has no row for (equity) is dropped from the options.
        assertEquals(listOf("mpesa", "airtel"), giveMethodOptions(res).map { it.key })
    }

    @Test
    fun `only what the server takes AND this app can carry is selectable`() {
        val options = giveMethodOptions(json.decodeFromString<GivingMethodsRes>(live)).associateBy { it.key }
        assertTrue(options.getValue("mpesa").selectable)
        assertFalse("switched off", options.getValue("airtel").selectable)
        assertFalse("USD on a KSh form", options.getValue("paypal").selectable)
        assertFalse("disabled card", options.getValue("card").selectable)
        // Even live, a card is not selectable here — no Stripe SDK to confirm it.
        assertFalse(options.getValue("card").copy(enabled = true).selectable)
        // A live Airtel in shillings would be.
        assertTrue(options.getValue("airtel").copy(enabled = true).selectable)
    }

    @Test
    fun `no answer at all leaves M-Pesa alone, on the server's own terms`() {
        assertEquals(listOf(FALLBACK_MPESA), giveMethodOptions(null))
        assertEquals(listOf(FALLBACK_MPESA), giveMethodOptions(GivingMethodsRes()))
        assertTrue(FALLBACK_MPESA.selectable)
        assertTrue(FALLBACK_MPESA.recurring)
        assertTrue(FALLBACK_MPESA.needsPhone)
        assertEquals(100L, FALLBACK_MPESA.minMinor)
        assertEquals(25_000_000L, FALLBACK_MPESA.maxMinor)
        assertEquals("mpesa", defaultGiveMethod(null, giveMethodOptions(null)))
    }

    @Test
    fun `the form starts on the server's default, and a pick stands only while it can be taken`() {
        val res = json.decodeFromString<GivingMethodsRes>(live)
        val options = giveMethodOptions(res)
        assertEquals("mpesa", defaultGiveMethod(res, options))
        assertEquals("mpesa", effectiveGiveMethod(null, res, options)?.key)
        // A pick the form cannot take (switched off, a card) falls back.
        assertEquals("mpesa", effectiveGiveMethod("airtel", res, options)?.key)
        assertEquals("mpesa", effectiveGiveMethod("card", res, options)?.key)
        // A default this form cannot take is skipped for the first it can.
        val paypalFirst = res.copy(defaultMethod = "paypal")
        assertEquals("mpesa", defaultGiveMethod(paypalFirst, giveMethodOptions(paypalFirst)))
        // Nothing selectable at all → no method (the plan says giving is unavailable).
        val allOff = json.decodeFromString<GivingMethodsRes>(
            """{"methods":[{"key":"mpesa","label":"M-Pesa","enabled":false,"unavailable_reason":"unavailable"}],"default_method":null}""",
        )
        assertNull(effectiveGiveMethod("mpesa", allOff, giveMethodOptions(allOff)))
    }

    @Test
    fun `a method the form cannot take wears SOON, or UNAVAILABLE when switched off`() {
        val res = json.decodeFromString<GivingMethodsRes>(live)
        val options = giveMethodOptions(res).associateBy { it.key }
        assertEquals("SOON", methodChipLabel(options["airtel"]))
        assertEquals("SOON", methodChipLabel(options["card"]))
        assertEquals("SOON", methodChipLabel(options["paypal"]))
        assertEquals("SOON", methodChipLabel(null)) // not listed by the server
        assertEquals("UNAVAILABLE", methodChipLabel(options.getValue("mpesa").copy(enabled = false, unavailableReason = "unavailable")))
    }

    // ── The prompt number ──

    @Test
    fun `Kenyan mobile numbers normalise to E164 however they are written`() {
        mapOf(
            "0711222333" to "+254711222333",
            "0711 222 333" to "+254711222333",
            "+254 711 222 333" to "+254711222333",
            "+254711222333" to "+254711222333",
            "254711222333" to "+254711222333",
            "711222333" to "+254711222333",
            "0110 123 456" to "+254110123456",
            "0110123456" to "+254110123456",
            "+254110123456" to "+254110123456",
            "254110123456" to "+254110123456",
            "(0711) 222-333" to "+254711222333",
        ).forEach { (raw, e164) -> assertEquals(raw, e164, kenyanMobileE164(raw)) }
    }

    @Test
    fun `anything that is not a Kenyan mobile is refused`() {
        listOf(
            "", "   ", "12345", "0711 222 33", "07112223334", "020 123 4567", "0812345678",
            "+1 202 555 0143", "+255 711 222 333", "+254 0711 222 333", "abc", "00254711222333",
        ).forEach { raw -> assertNull("\"$raw\"", kenyanMobileE164(raw)) }
        assertNull(kenyanMobileE164(null))
    }

    @Test
    fun `a number is shown the way a Kenyan reads it`() {
        assertEquals("0711 222 333", kenyanMobileDisplay("+254711222333"))
        assertEquals("0110 123 456", kenyanMobileDisplay("254110123456"))
        assertEquals("12345", kenyanMobileDisplay("12345")) // not one: as given
        assertEquals("", kenyanMobileDisplay(null))
    }

    @Test
    fun `the prompt starts on this device's last number, else the profile's, else none`() {
        assertEquals("+254722000111", promptPhonePrefill(lastUsed = "+254722000111", phoneOnFile = "+254711222333"))
        assertEquals("+254711222333", promptPhonePrefill(lastUsed = "", phoneOnFile = "+254711222333"))
        assertEquals("+254711222333", promptPhonePrefill(lastUsed = "garbage", phoneOnFile = "0711 222 333"))
        assertNull(promptPhonePrefill(lastUsed = null, phoneOnFile = null))
        assertNull(promptPhonePrefill(lastUsed = "", phoneOnFile = "020 123 4567"))
    }

    @Test
    fun `a schedule carries a number only when it is not the profile's`() {
        assertNull(schedulePhoneFor("+254711222333", "+254711222333"))
        assertNull(schedulePhoneFor("0711 222 333", "+254711222333")) // same number, written locally
        assertEquals("+254722000111", schedulePhoneFor("0722 000 111", "+254711222333"))
        assertEquals("+254722000111", schedulePhoneFor("0722000111", null))
        assertNull(schedulePhoneFor(null, "+254711222333"))
        assertNull(schedulePhoneFor("12345", null))
    }
}
