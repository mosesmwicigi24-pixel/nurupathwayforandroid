// "What is this pledge for?" on the wire (pledge names, contract 2026-09-25),
// pinned: an option travels as its target and NO title; General travels as
// nothing; a custom name travels as `title` ONLY, trimmed and length-checked.
// Plus the picker's grouping and its fallback when the server sends no options.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.PledgeOption
import java.time.LocalDate

class PledgeRequestLogicTest {
    private val general = PledgeOption(key = "general", title = "General partnership", kind = "general")
    private val fund = PledgeOption(key = "fund:mission", title = "Mission", kind = "fund", fund = "mission")
    private val campaign = PledgeOption(key = "campaign:c1", title = "New roof", kind = "campaign", campaignId = "c1")
    private val need = PledgeOption(key = "need:n1", title = "Sound desk", kind = "need", needId = "n1")

    private fun body(target: PledgeFor?, shape: String = "monthly") =
        buildPledgeBody(shape = shape, amountMajor = 1_000, target = target, dueDay = 5, dueOn = LocalDate.of(2026, 12, 31), autoMethod = null)

    @Test
    fun `general partnership sends no target and no title`() {
        val b = body(PledgeFor.Option(general))
        assertNull(b.fund); assertNull(b.campaignId); assertNull(b.needId); assertNull(b.title)
        // A null target is General too.
        val n = body(null)
        assertNull(n.fund); assertNull(n.campaignId); assertNull(n.needId); assertNull(n.title)
        assertEquals(100_000, b.amountMinor)
        assertEquals(5, b.dueDay)
    }

    @Test
    fun `a fund option sends the fund code and no title`() {
        val b = body(PledgeFor.Option(fund))
        assertEquals("mission", b.fund)
        assertNull(b.campaignId); assertNull(b.needId); assertNull(b.title)
    }

    @Test
    fun `a campaign option sends campaign_id`() {
        val b = body(PledgeFor.Option(campaign), shape = "total")
        assertEquals("c1", b.campaignId)
        assertNull(b.fund); assertNull(b.needId); assertNull(b.title)
        assertEquals(100_000, b.targetMinor)
        assertEquals("2026-12-31", b.dueOn)
    }

    @Test
    fun `a need option sends need_id`() {
        val b = body(PledgeFor.Option(need))
        assertEquals("n1", b.needId)
        assertNull(b.fund); assertNull(b.campaignId); assertNull(b.title)
    }

    @Test
    fun `a custom name sends title only, trimmed`() {
        val b = body(PledgeFor.Custom("  Mum's house  "))
        assertEquals("Mum's house", b.title)
        assertNull(b.fund); assertNull(b.campaignId); assertNull(b.needId)
    }

    @Test
    fun `a custom name is 2 to 60 characters once trimmed`() {
        assertFalse(pledgeTitleValid(""))
        assertFalse(pledgeTitleValid(" a "))
        assertTrue(pledgeTitleValid("ab"))
        assertTrue(pledgeTitleValid("x".repeat(60)))
        assertFalse(pledgeTitleValid("x".repeat(61)))
        // An invalid custom name never leaks onto the wire as a title.
        assertNull(body(PledgeFor.Custom("a")).title)
    }

    @Test
    fun `the review step names the choice`() {
        assertEquals("General partnership", pledgeForTitle(null))
        assertEquals("General partnership", pledgeForTitle(PledgeFor.Option(general)))
        assertEquals("Mission", pledgeForTitle(PledgeFor.Option(fund)))
        assertEquals("Mum's house", pledgeForTitle(PledgeFor.Custom(" Mum's house ")))
    }

    @Test
    fun `options fall back to general plus the funds when the server sends none`() {
        val fallback = pledgeOptionsOrFallback(emptyList())
        assertEquals("general", fallback.first().kind)
        assertEquals(GIVE_FUNDS.map { it.id }, fallback.drop(1).map { it.fund })
        assertTrue(fallback.drop(1).all { it.kind == "fund" })
        // The server's list is used as sent, in its order.
        val server = listOf(general, fund, campaign, need)
        assertEquals(server, pledgeOptionsOrFallback(server))
    }

    @Test
    fun `options group by kind in the server's order with the section labels`() {
        val other = PledgeOption(key = "x", title = "Something new", kind = "mystery")
        val groups = groupedPledgeOptions(listOf(general, need, fund, other, campaign))
        assertEquals(listOf("General", "Funds", "Campaigns", "Department needs", "Other"), groups.map { it.label })
        assertEquals(listOf(need), groups[3].options)
        assertEquals(listOf(other), groups[4].options)
        // Empty kinds have no section.
        assertEquals(listOf("General", "Funds"), groupedPledgeOptions(listOf(general, fund)).map { it.label })
    }

    @Test
    fun `a card is picked by key, and General by kind alone`() {
        assertTrue(pledgeOptionSelected(fund, PledgeFor.Option(fund)))
        assertFalse(pledgeOptionSelected(fund, PledgeFor.Option(campaign)))
        // The server's General row and the flow's local default share a kind, not a key.
        val serverGeneral = PledgeOption(key = "partnership", title = "General partnership", kind = "general")
        assertTrue(pledgeOptionSelected(serverGeneral, PledgeFor.Option(GENERAL_PLEDGE_OPTION)))
        assertFalse(pledgeOptionSelected(fund, PledgeFor.Option(GENERAL_PLEDGE_OPTION)))
        // A custom name — or nothing — lights no option's card.
        assertFalse(pledgeOptionSelected(general, PledgeFor.Custom("Mum's house")))
        assertFalse(pledgeOptionSelected(general, null))
    }

    // ── Editing a pledge (iOS EditPledgeSheet) ──

    @Test
    fun `the edit offers iOS's amounts in the pledge's own money`() {
        assertEquals(listOf(50_000, 100_000, 200_000, 500_000, 1_000_000, 2_000_000), pledgeEditPresets("KES"))
        assertEquals(listOf(500, 1_000, 2_500, 5_000, 10_000), pledgeEditPresets("usd"))
        assertEquals("20,000", pledgeEditAmountText(2_000_000, "KES"))
        assertEquals("25.50", pledgeEditAmountText(2_550, "USD"))
    }

    @Test
    fun `the name field says what a name does, before and after one is given`() {
        assertEquals("Named after what it's for. Give it a name of your own if you like — 2–60 characters.", pledgeNameHelp(false))
        assertEquals("2–60 characters. Clear it to go back to the name of what it's for.", pledgeNameHelp(true))
    }

    @Test
    fun `clearing a name travels only when there was one of the member's own`() {
        val derived = org.nuruplace.member.data.net.Pledge(pledgeId = "p", title = "Tithe")
        val named = derived.copy(title = "School fees", customTitle = "School fees")
        // Untouched: nothing.
        assertNull(pledgeEditTitlePatch(derived, " Tithe "))
        assertNull(pledgeEditTitlePatch(named, "School fees"))
        // A new name: it travels.
        assertEquals(kotlinx.serialization.json.JsonPrimitive("Mum's house"), pledgeEditTitlePatch(derived, " Mum's house "))
        // Cleared: back to the derived name — only when there was a custom one.
        assertEquals(kotlinx.serialization.json.JsonNull, pledgeEditTitlePatch(named, "  "))
        assertNull(pledgeEditTitlePatch(derived, "  "))
    }

    // ── The new-pledge flow's steps and words (iOS NewPledgeFlow) ──

    @Test
    fun `a total pledge walks five steps - no automatic collection, and Back from its review lands on its date`() {
        assertEquals(PledgeStep.entries, pledgeSteps(monthly = true))
        val total = pledgeSteps(monthly = false)
        assertEquals(listOf(PledgeStep.Shape, PledgeStep.Amount, PledgeStep.Target, PledgeStep.Due, PledgeStep.Review), total)
        // The step before the review is the date, never the skipped question.
        assertEquals(PledgeStep.Due, total[total.indexOf(PledgeStep.Review) - 1])
    }

    @Test
    fun `each step asks its question in iOS's words, by the shape of the promise`() {
        assertEquals("What shape is the promise?", pledgeStepTitle(PledgeStep.Shape, true))
        assertEquals("How much each month?", pledgeStepTitle(PledgeStep.Amount, true))
        assertEquals("How much in total?", pledgeStepTitle(PledgeStep.Amount, false))
        assertEquals("What is this pledge for?", pledgeStepTitle(PledgeStep.Target, true))
        assertEquals("Which day of the month?", pledgeStepTitle(PledgeStep.Due, true))
        assertEquals("By when?", pledgeStepTitle(PledgeStep.Due, false))
        assertEquals("Collect it automatically?", pledgeStepTitle(PledgeStep.Auto, true))
        assertEquals("Here is your pledge", pledgeStepTitle(PledgeStep.Review, true))
        assertEquals("We'll remind you a few days before, if you'd like.", pledgeStepSubtitle(PledgeStep.Due, true))
        assertEquals("The date you would like the total reached by.", pledgeStepSubtitle(PledgeStep.Due, false))
        assertEquals("Read it once more. Nothing is charged by creating it.", pledgeStepSubtitle(PledgeStep.Review, false))
    }

    @Test
    fun `the amount starts at KSh 2,000 among iOS's suggestions, a total's date three months on`() {
        assertEquals(listOf(500, 1_000, 2_000, 5_000, 10_000, 20_000), NEW_PLEDGE_PRESETS)
        assertEquals(2_000, NEW_PLEDGE_DEFAULT_AMOUNT)
        val today = LocalDate.of(2026, 9, 28)
        assertEquals(LocalDate.of(2026, 12, 28), newPledgeDefaultDueOn(today))
        assertFalse(newPledgeDueOnAllowed(today, today))
        assertTrue(newPledgeDueOnAllowed(today.plusDays(1), today))
    }

    @Test
    fun `automatic collection runs on the server's recurring rails of the two a pledge takes`() {
        // No answer yet: M-Pesa alone.
        assertEquals(listOf("mpesa"), pledgeAutoRails(null).map { it.key })
        val res = org.nuruplace.member.data.net.GivingMethodsRes(
            methods = listOf(
                org.nuruplace.member.data.net.GivingMethodInfo(key = "mpesa", label = "M-Pesa", enabled = true, currency = "KES", recurring = true),
                org.nuruplace.member.data.net.GivingMethodInfo(key = "airtel", label = "Airtel Money", enabled = true, currency = "KES", recurring = false),
                org.nuruplace.member.data.net.GivingMethodInfo(key = "paypal", label = "PayPal", enabled = true, currency = "USD", recurring = true),
            ),
        )
        assertEquals(listOf("mpesa"), pledgeAutoRails(res).map { it.key })
        // M-Pesa switched off: nothing can collect it — the toggle holds.
        val off = res.copy(methods = res.methods.map { if (it.key == "mpesa") it.copy(enabled = false) else it })
        assertTrue(pledgeAutoRails(off).isEmpty())
        assertEquals("On the 5th of every month, by mobile money.", pledgeAutoCaption(5))
        assertEquals(
            "Never today — then on the 22nd of each month. You can stop it at any time from your recurring gifts.",
            pledgeAutoOnNote(22),
        )
    }

    @Test
    fun `the review repeats every choice in iOS's words`() {
        assertEquals(
            listOf(
                "Shape" to "Monthly",
                "Each month" to "KSh 2,000",
                "For" to "General partnership",
                "Due day" to "The 5th of each month",
                "Collected" to "Automatically · M-Pesa",
                "First collection" to "5 October",
            ),
            pledgeReviewRows(true, 2_000, "General partnership", 5, null, "M-Pesa", "5 October"),
        )
        assertEquals(
            listOf(
                "Shape" to "A total, by a date",
                "Total" to "KSh 50,000",
                "For" to "School fees for Grace",
                "By" to "Mon 28 Dec 2026",
                "Collected" to "By you, with Pay now",
            ),
            pledgeReviewRows(false, 50_000, "School fees for Grace", 5, LocalDate.of(2026, 12, 28), null, "5 October"),
        )
        // A monthly pledge left to the member: no First collection row.
        assertEquals("By you, with Pay now", pledgeReviewRows(true, 1_000, "Tithe", 1, null, null, "1 October").last().second)
    }

    @Test
    fun `a failed create says whether the church answered`() {
        assertEquals(
            "We couldn't hear back from the church. Try again — if your pledge was made, it won't be made twice.",
            pledgeCreateError(noAnswer = true, serverWords = null),
        )
        assertEquals("M-Pesa can't take that amount.", pledgeCreateError(noAnswer = false, serverWords = "M-Pesa can't take that amount."))
        assertEquals("Couldn't create the pledge. Nothing has changed.", pledgeCreateError(noAnswer = false, serverWords = " "))
    }

    // ── leaving a pledge part-made (EXPERIENCE.md §7.2 #7) ──

    private val began = PledgeEntries(
        amountMajor = NEW_PLEDGE_DEFAULT_AMOUNT, customAmount = "", target = PledgeFor.Option(general), customName = "",
        dueDay = 4, dueOn = LocalDate.of(2027, 1, 4), autoCharge = false,
    )

    @Test
    fun `on the first step with nothing changed, Close closes at once`() {
        assertFalse(pledgeLeaveAsks(0, began, began))
    }

    @Test
    fun `past the first step, Close asks — whatever was chosen`() {
        for (step in 1..5) assertTrue("step ${step + 1}", pledgeLeaveAsks(step, began, began))
        assertTrue(pledgeLeaveAsks(4, began.copy(amountMajor = 5_000, autoCharge = true), began))
    }

    @Test
    fun `back on the first step after changes, Close still asks`() {
        assertTrue(pledgeLeaveAsks(0, began.copy(amountMajor = 5_000), began))
        assertTrue(pledgeLeaveAsks(0, began.copy(customAmount = "7500", amountMajor = 7_500), began))
        assertTrue(pledgeLeaveAsks(0, began.copy(target = PledgeFor.Option(fund)), began))
        assertTrue(pledgeLeaveAsks(0, began.copy(target = PledgeFor.Custom("Roof"), customName = "Roof"), began))
        assertTrue(pledgeLeaveAsks(0, began.copy(dueDay = 15), began))
        assertTrue(pledgeLeaveAsks(0, began.copy(dueOn = LocalDate.of(2027, 3, 1)), began))
        assertTrue(pledgeLeaveAsks(0, began.copy(autoCharge = true), began))
    }

    @Test
    fun `the question, in both apps' words`() {
        assertEquals("Leave this pledge?", PLEDGE_LEAVE_TITLE)
        assertEquals("What you entered won't be kept.", PLEDGE_LEAVE_LINE)
        assertEquals("Keep editing", PLEDGE_LEAVE_STAY)
        assertEquals("Leave", PLEDGE_LEAVE_GO)
    }
}
