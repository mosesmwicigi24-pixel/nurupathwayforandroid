// The Partners statement screen's derivations (PartnersStatementLogic.kt),
// pinned: the year chips from the join year, "prefer the server's numbers,
// else the local rule", the pledge rows (server's or derived), pledge-tied
// payments by month with subtotals, and the two progress lines. Both clients
// show these; only this file decides them.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.PartnerSeason
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.PledgeProgress
import org.nuruplace.member.data.net.StatementFaithfulness
import org.nuruplace.member.data.net.StatementImpact
import org.nuruplace.member.data.net.StatementMonthStatus
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPendingPayment
import org.nuruplace.member.data.net.StatementPledge
import java.time.LocalDate

class PartnersStatementLogicTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 25)

    private fun monthly(id: String = "m1", amount: Int = 200_000, dueDay: Int = 5, createdAt: String? = null, status: String = "active", title: String? = null) =
        Pledge(pledgeId = id, shape = "monthly", amountMinor = amount, dueDay = dueDay, createdAt = createdAt, status = status, title = title)

    private fun total(id: String = "t1", target: Int = 5_000_000, dueOn: String, status: String = "active") =
        Pledge(pledgeId = id, shape = "total", targetMinor = target, dueOn = dueOn, status = status)

    private fun payment(amount: Int, pledgeId: String?, at: String?, id: String = "tx-$amount-$at") =
        StatementPayment(transactionId = id, amountMinor = amount, pledgeId = pledgeId, at = at)

    // ── Year chips ──

    @Test
    fun `year chips run from this year back to the join year, at most four`() {
        assertEquals(listOf(2026), partnerStatementYears(2026, 2026))
        assertEquals(listOf(2026, 2025), partnerStatementYears(2026, 2025))
        assertEquals(listOf(2026, 2025, 2024, 2023), partnerStatementYears(2026, 2023))
        // A partner since 2019 still sees only four.
        assertEquals(listOf(2026, 2025, 2024, 2023), partnerStatementYears(2026, 2019))
    }

    @Test
    fun `year chips fall back to this year without a join year or with one in the future`() {
        assertEquals(listOf(2026), partnerStatementYears(2026, null))
        assertEquals(listOf(2026), partnerStatementYears(2026, 2027))
    }

    // ── Summary: server first, else local ──

    @Test
    fun `summary prefers the server's numbers when it sends pledged and paid`() {
        // The local rule over these inputs would say 12 × 200,000 pledged and 0 paid;
        // the server's figures win whenever they are present.
        val s = GivingStatement(year = 2026, pledgedMinor = 600_000, paidMinor = 400_000, remainingMinor = 200_000)
        assertEquals(StatementSummary(600_000, 400_000, 200_000), partnerStatementSummary(2026, s, listOf(monthly())))
    }

    @Test
    fun `summary derives remaining when the server sends pledged and paid only`() {
        val s = GivingStatement(year = 2026, pledgedMinor = 600_000, paidMinor = 400_000)
        assertEquals(StatementSummary(600_000, 400_000, 200_000), partnerStatementSummary(2026, s, emptyList()))
        // Overpaid never goes negative.
        assertEquals(0, partnerStatementSummary(2026, GivingStatement(pledgedMinor = 100, paidMinor = 900), emptyList()).remainingMinor)
    }

    @Test
    fun `summary falls back to the local rule when the server sends none`() {
        val payments = listOf(payment(200_000, "m1", "2026-10-05T09:00:00Z"), payment(999, null, "2026-10-06T09:00:00Z"))
        val s = GivingStatement(year = 2026, payments = payments)
        val pledges = listOf(monthly(createdAt = "2026-10-01T00:00:00Z")) // Oct, Nov, Dec = 600,000
        assertEquals(StatementSummary(600_000, 200_000, 400_000), partnerStatementSummary(2026, s, pledges))
        // Half a contract (paid without pledged) is not the server's answer either.
        assertEquals(StatementSummary(600_000, 200_000, 400_000), partnerStatementSummary(2026, s.copy(paidMinor = 1), pledges))
    }

    // ── Pledge rows ──

    @Test
    fun `pledge rows are the server's when present, even an empty list`() {
        val server = listOf(StatementPledge(pledgeId = "p", title = "School fees", pledgedMinor = 1, paidMinor = 2, kept = 3, dueCount = 4))
        assertEquals(server, partnerStatementPledges(2026, GivingStatement(pledges = server), listOf(monthly()), today))
        assertTrue(partnerStatementPledges(2026, GivingStatement(pledges = emptyList()), listOf(monthly()), today).isEmpty())
    }

    @Test
    fun `pledge rows fall back to the partnership's live pledges with local math`() {
        val pledges = listOf(
            monthly(id = "m1", createdAt = "2026-06-10T00:00:00Z", title = "School fees"), // due 5 Jul..5 Dec = 6 owed; elapsed by 25 Sep: Jul, Aug, Sep = 3
            total(id = "t1", dueOn = "2026-12-15"),
            monthly(id = "c", status = "cancelled"),
            total(id = "next", dueOn = "2027-03-01"),                                     // nothing owed or paid this year
        )
        val payments = listOf(
            payment(200_000, "m1", "2026-07-05T09:00:00Z"), payment(200_000, "m1", "2026-08-05T09:00:00Z"),
            payment(1_000_000, "t1", "2026-08-20T09:00:00Z"), payment(50_000, null, "2026-08-21T09:00:00Z"),
        )
        val rows = partnerStatementPledges(2026, GivingStatement(payments = payments), pledges, today)
        assertEquals(listOf("m1", "t1"), rows.map { it.pledgeId })
        val m = rows[0]
        assertEquals("School fees", m.title)
        assertEquals(6 * 200_000, m.pledgedMinor)
        assertEquals(400_000, m.paidMinor)
        assertEquals(2 to 3, m.kept to m.dueCount)
        val t = rows[1]
        assertEquals(5_000_000, t.pledgedMinor)
        assertEquals(1_000_000, t.paidMinor)
        assertEquals(0 to 0, t.kept to t.dueCount)
        assertEquals("total", t.shape)
    }

    @Test
    fun `kept in a past year counts the whole year, in a future year nothing`() {
        val pl = monthly(createdAt = "2025-03-15T00:00:00Z") // due 5 Apr..5 Dec 2025 = 9
        assertEquals(9 to 9, keptInYear(pl, 12, 2025, today))
        assertEquals(0 to 0, keptInYear(pl, 12, 2027, today))
        // This year, through today (25 Sep): Jan..Sep = 9 elapsed of 12.
        assertEquals(4 to 9, keptInYear(pl, 4, 2026, today))
        assertEquals(0 to 0, keptInYear(total(dueOn = "2026-12-01"), 3, 2026, today))
    }

    @Test
    fun `progress line reads paid and kept for monthly, paid alone for total or nothing due`() {
        assertEquals("KSh 6,000 paid · 3 of 4 kept", pledgeProgressLine(StatementPledge(shape = "monthly", paidMinor = 600_000, kept = 3, dueCount = 4)))
        assertEquals("KSh 20,000 paid", pledgeProgressLine(StatementPledge(shape = "total", paidMinor = 2_000_000, kept = 0, dueCount = 0)))
        assertEquals("KSh 0 paid", pledgeProgressLine(StatementPledge(shape = "monthly", paidMinor = 0, kept = 0, dueCount = 0)))
    }

    @Test
    fun `amount line names the rhythm or the deadline`() {
        assertEquals("KSh 2,000 monthly · due on the 5th", pledgeAmountLine(StatementPledge(shape = "monthly", amountMinor = 200_000, dueDay = 5)))
        assertEquals("KSh 2,000 monthly", pledgeAmountLine(StatementPledge(shape = "monthly", amountMinor = 200_000)))
        assertEquals("KSh 50,000 by Dec 2026", pledgeAmountLine(StatementPledge(shape = "total", targetMinor = 5_000_000, dueOn = "2026-12-15")))
    }

    // ── Payments by month ──

    @Test
    fun `payments group by month newest first with subtotals, pledge-tied only`() {
        val payments = listOf(
            payment(100_000, "m1", "2026-07-05T09:00:00Z", id = "jul5"),
            payment(300_000, "m1", "2026-09-20T09:00:00Z", id = "sep20"),
            payment(200_000, "t1", "2026-09-03T09:00:00Z", id = "sep3"),
            payment(50_000, null, "2026-09-10T09:00:00Z", id = "outside"),   // a gift outside a pledge: not here
            payment(1, "", "2026-09-11T09:00:00Z", id = "blank"),             // blank pledge id = outside too
            payment(400_000, "m1", "2026-09-20T17:00:00Z", id = "sep20pm"),
        )
        val months = paymentsByMonth(payments)
        assertEquals(listOf(9, 7), months.map { it.month })
        assertEquals(listOf(2026, 2026), months.map { it.year })
        // Newest first inside the month, the later same-day gift ahead of the earlier.
        assertEquals(listOf("sep20pm", "sep20", "sep3"), months[0].payments.map { it.transactionId })
        assertEquals(900_000, months[0].subtotalMinor)
        assertEquals(listOf("jul5"), months[1].payments.map { it.transactionId })
        assertEquals(100_000, months[1].subtotalMinor)
        assertEquals(1_000_000, statementYearTotal(months))
        assertTrue(months.none { it.undated })
    }

    @Test
    fun `undated payments keep their money in a trailing group`() {
        val months = paymentsByMonth(listOf(payment(100_000, "m1", "2026-02-05T09:00:00Z"), payment(70_000, "m1", null), payment(30_000, "m1", "not a date")))
        assertEquals(listOf(2, 0), months.map { it.month })
        assertTrue(months.last().undated)
        assertEquals(100_000, months.last().subtotalMinor)
        assertEquals(200_000, statementYearTotal(months))
    }

    @Test
    fun `no pledge payments means no months`() {
        assertTrue(paymentsByMonth(emptyList()).isEmpty())
        assertTrue(paymentsByMonth(listOf(payment(5, null, "2026-01-01T00:00:00Z"))).isEmpty())
    }

    // ── Statement v2: the impact-led top (spec §3d) ──

    @Test
    fun `disciples tile shows the count from one disciple up`() {
        assertEquals(DisciplesTile.Carried(1), disciplesTile(StatementImpact(paidMinor = 2_000_000, perDiscipleMinor = 2_000_000, disciplesCarried = 1, towardNextMinor = 0)))
        assertEquals(DisciplesTile.Carried(3), disciplesTile(StatementImpact(paidMinor = 6_600_000, perDiscipleMinor = 2_000_000, disciplesCarried = 3, towardNextMinor = 600_000)))
    }

    @Test
    fun `disciples tile never says zero - below the first it is progress toward it`() {
        val t = disciplesTile(StatementImpact(paidMinor = 600_000, perDiscipleMinor = 2_000_000, disciplesCarried = 0, towardNextMinor = 600_000))
        assertTrue(t is DisciplesTile.Toward)
        t as DisciplesTile.Toward
        assertEquals("KSh 6,000 of 20,000 toward carrying one disciple through a level", t.text)
        assertEquals(0.3f, t.fraction, 0.0001f)
        assertFalse(t.text.startsWith("0"))
        // Nothing paid yet still reads as progress, never "0 disciples".
        val none = disciplesTile(StatementImpact(perDiscipleMinor = 2_000_000)) as DisciplesTile.Toward
        assertEquals("KSh 0 of 20,000 toward carrying one disciple through a level", none.text)
        assertEquals(0f, none.fraction, 0f)
    }

    @Test
    fun `disciples tile tolerates a missing per-disciple cost and a missing toward_next`() {
        // per_disciple 0 → the tier costing (KSh 20,000), so no divide-by-zero.
        val noCost = disciplesTile(StatementImpact(paidMinor = 500_000, perDiscipleMinor = 0, disciplesCarried = 0, towardNextMinor = 500_000)) as DisciplesTile.Toward
        assertEquals(DISCIPLE_COST_MINOR, noCost.perDiscipleMinor)
        assertEquals("KSh 5,000 of 20,000 toward carrying one disciple through a level", noCost.text)
        // toward_next absent while money was paid → the paid amount's remainder.
        val noToward = disciplesTile(StatementImpact(paidMinor = 800_000, perDiscipleMinor = 2_000_000)) as DisciplesTile.Toward
        assertEquals(800_000, noToward.towardMinor)
        // Never past the goal, whatever the wire says.
        val over = disciplesTile(StatementImpact(perDiscipleMinor = 2_000_000, towardNextMinor = 9_000_000)) as DisciplesTile.Toward
        assertEquals(2_000_000, over.towardMinor)
        assertEquals(1f, over.fraction, 0f)
    }

    @Test
    fun `kept tile is kept on time plus late of due, hidden before anything is due`() {
        assertEquals("5 of 6", keptTileValue(StatementFaithfulness(keptOnTime = 4, late = 1, missed = 1, dueCount = 6)))
        assertNull(keptTileValue(StatementFaithfulness(dueCount = 0)))
        assertNull(keptTileValue(null))
    }

    @Test
    fun `compact amount rounds down and never overstates`() {
        assertEquals("950", compactAmount(95_000))
        assertEquals("1k", compactAmount(100_000))
        assertEquals("9.5k", compactAmount(959_900))
        assertEquals("22k", compactAmount(2_200_000))
        assertEquals("22k", compactAmount(2_299_900))
        assertEquals("999k", compactAmount(99_999_900))
        assertEquals("1.5M", compactAmount(159_000_000))
        assertEquals("12M", compactAmount(1_250_000_000))
        assertEquals("0", compactAmount(0))
        assertEquals("0", compactAmount(-500))
    }

    @Test
    fun `faithfulness strip is twelve months Jan to Dec whatever the wire order`() {
        val wire = listOf(
            StatementMonthStatus(month = 9, status = "kept"),
            StatementMonthStatus(month = 7, status = "late"),
            StatementMonthStatus(month = 8, status = "MISSED"),
            StatementMonthStatus(month = 10, status = "upcoming"),
            StatementMonthStatus(month = 11, status = "something new"),
        )
        val marks = faithfulnessMarks(wire)!!
        assertEquals(12, marks.size)
        assertEquals(MonthMark.None, marks[0])
        assertEquals(MonthMark.Late, marks[6])
        assertEquals(MonthMark.Missed, marks[7])
        assertEquals(MonthMark.Kept, marks[8])
        assertEquals(MonthMark.Upcoming, marks[9])
        assertEquals(MonthMark.None, marks[10]) // unknown status reads as none
        assertEquals(MonthMark.None, marks[11]) // left out reads as none
    }

    @Test
    fun `faithfulness strip hides without months or with nothing but none`() {
        assertNull(faithfulnessMarks(null))
        assertNull(faithfulnessMarks(emptyList()))
        assertNull(faithfulnessMarks((1..12).map { StatementMonthStatus(month = it, status = "none") }))
    }

    @Test
    fun `faithfulness line says kept, late months and next due`() {
        val marks = listOf("kept", "kept", "kept", "kept", "kept", "kept", "late", "kept", "kept", "upcoming", "upcoming", "upcoming").map(::monthMark)
        assertEquals(
            "Kept on time 8 months · late 1 (Jul) · next due 5 Oct",
            faithfulnessLine(marks, LocalDate.of(2026, 10, 5), today),
        )
        val twoLate = listOf("kept", "none", "none", "none", "none", "none", "late", "late", "missed", "none", "none", "none").map(::monthMark)
        assertEquals("Kept on time 1 month · late 2 (Jul, Aug)", faithfulnessLine(twoLate, null, today))
        // A new partner with nothing behind them: just the next due.
        val fresh = List(9) { MonthMark.None } + List(3) { MonthMark.Upcoming }
        assertEquals("Next due 5 Oct", faithfulnessLine(fresh, LocalDate.of(2026, 10, 5), today))
        // Next year's date carries its year.
        assertEquals("Next due 5 Jan 2027", faithfulnessLine(fresh, LocalDate.of(2027, 1, 5), today))
        assertNull(faithfulnessLine(fresh, null, today))
    }

    @Test
    fun `next pledge due is the earliest server next_due across active monthly pledges`() {
        val p = Partnership(
            due = listOf(
                DueItem(kind = "schedule", id = "s", dueOn = "2026-09-28"),     // the DUE list is not read
                DueItem(kind = "pledge", id = "b", dueOn = "2026-09-26"),
            ),
            pledges = listOf(
                Pledge(pledgeId = "c", status = "active", progress = PledgeProgress(nextDue = "2026-10-01")),
                Pledge(pledgeId = "d", status = "paused", progress = PledgeProgress(nextDue = "2026-09-26")),   // not active
                Pledge(pledgeId = "t", shape = "total", status = "active", dueOn = "2026-09-27",
                    progress = PledgeProgress(nextDue = "2026-09-27")),                                         // not monthly
                Pledge(pledgeId = "o", status = "active", progress = PledgeProgress(nextDue = "2026-09-20")),   // behind us
            ),
        )
        assertEquals(LocalDate.of(2026, 10, 1), nextPledgeDue(p, today))
        assertNull(nextPledgeDue(null, today))
        assertNull(nextPledgeDue(Partnership(), today))
    }

    @Test
    fun `a pledge paid today is next due next month, not today`() {
        // today = 25 Sep. The instalment due today was paid: the server's ledger
        // moved next_due to 25 Oct, though a DUE row for today may linger until
        // the payment settles.
        val paidToday = Partnership(
            due = listOf(DueItem(kind = "pledge", id = "p1", dueOn = "2026-09-25", amountMinor = 100_000)),
            pledges = listOf(Pledge(pledgeId = "p1", status = "active", dueDay = 25, progress = PledgeProgress(nextDue = "2026-10-25"))),
        )
        assertEquals(LocalDate.of(2026, 10, 25), nextPledgeDue(paidToday, today))
        assertEquals(
            "Kept on time 1 month · next due 25 Oct",
            faithfulnessLine(List(8) { MonthMark.None } + MonthMark.Kept + List(3) { MonthMark.None }, nextPledgeDue(paidToday, today), today),
        )
        // Paid ahead as well: the ledger is already at November.
        val prepaid = paidToday.copy(pledges = listOf(paidToday.pledges.single().copy(progress = PledgeProgress(nextDue = "2026-11-25"))))
        assertEquals(LocalDate.of(2026, 11, 25), nextPledgeDue(prepaid, today))
    }

    @Test
    fun `due_day stands in only when the server sent no next_due`() {
        fun on(dueDay: Int, nextDue: String? = null) =
            Partnership(pledges = listOf(Pledge(pledgeId = "p", status = "active", dueDay = dueDay, progress = PledgeProgress(nextDue = nextDue))))
        assertEquals(LocalDate.of(2026, 10, 5), nextPledgeDue(on(5), today))     // the 5th has passed this month
        assertEquals(LocalDate.of(2026, 9, 25), nextPledgeDue(on(25), today))    // due today, no ledger to say otherwise
        assertEquals(LocalDate.of(2026, 9, 28), nextPledgeDue(on(30), today))    // days are 1–28
        // A next_due from the server always wins over the day.
        assertEquals(LocalDate.of(2026, 10, 25), nextPledgeDue(on(25, nextDue = "2026-10-25"), today))
        assertEquals(LocalDate.of(2026, 10, 25), nextDueDayDate(25, LocalDate.of(2026, 9, 26)))
    }

    @Test
    fun `remaining this year sums the server's per-pledge remaining, hidden without it`() {
        val rows = listOf(
            StatementPledge(pledgeId = "a", remainingYearMinor = 600_000),
            StatementPledge(pledgeId = "b", remainingYearMinor = 0),
            StatementPledge(pledgeId = "c", remainingYearMinor = 1_000_000),
        )
        assertEquals(1_600_000, remainingThisYear(rows))
        assertNull(remainingThisYear(listOf(StatementPledge(pledgeId = "a", pledgedMinor = 5, paidMinor = 1))))
        assertNull(remainingThisYear(emptyList()))
    }

    @Test
    fun `church raised shows on a need pledge only, rounded down and held to 100`() {
        assertEquals("Church raised 42%", churchRaisedLine(StatementPledge(churchProgressPercent = 42.9)))
        assertEquals("Church raised 100%", churchRaisedLine(StatementPledge(churchProgressPercent = 130.0)))
        assertEquals("Church raised 0%", churchRaisedLine(StatementPledge(churchProgressPercent = -3.0)))
        assertNull(churchRaisedLine(StatementPledge()))
    }

    @Test
    fun `season line is church-wide, drops a zero half and hides with nothing to say`() {
        assertEquals(
            "4 levels completed and 12 plans finished across the church while you have partnered.",
            seasonLine(PartnerSeason(from = "2026-01-05", levelsCompleted = 4, modulesCompleted = 30, plansFinished = 12)),
        )
        assertEquals(
            "1 level completed and 1 plan finished across the church while you have partnered.",
            seasonLine(PartnerSeason(levelsCompleted = 1, plansFinished = 1)),
        )
        assertEquals("3 plans finished across the church while you have partnered.", seasonLine(PartnerSeason(plansFinished = 3)))
        assertNull(seasonLine(PartnerSeason()))
        assertNull(seasonLine(null))
    }

    // ── Pending rows (freshness fix, owner 2026-09-26) ──

    private fun pending(id: String, amount: Int = 100_000, method: String? = "mpesa", at: String? = "2026-09-26T19:04:00Z", pledgeId: String? = "p1") =
        StatementPendingPayment(transactionId = id, amountMinor = amount, method = method, at = at, pledgeId = pledgeId, pledgeTitle = "General partnership")

    @Test
    fun `pending chip says what the payment is waiting for`() {
        assertEquals("Waiting for M-Pesa", pendingChipText("mpesa"))
        assertEquals("Waiting for M-Pesa", pendingChipText(" MPESA "))
        assertEquals("Waiting for Airtel Money", pendingChipText("airtel"))
        assertEquals("Processing", pendingChipText("card"))
        assertEquals("Processing", pendingChipText("paypal"))
        assertEquals("Processing", pendingChipText(null))
        assertEquals("Processing", pendingChipText(""))
    }

    @Test
    fun `pending rows are pledge-tied, newest first, and never repeat a row that has settled`() {
        val s = GivingStatement(
            year = 2026,
            payments = listOf(payment(200_000, "p1", "2026-09-05T09:00:00Z", id = "settled")),
            pending = listOf(
                pending("older", at = "2026-09-20T08:00:00Z"),
                pending("settled"),                         // settled between the reads — shown once, as settled
                pending("gift", pledgeId = null),           // not a pledge payment — never on this statement
                pending("newer", method = "airtel", at = "2026-09-26T19:04:00Z"),
            ),
        )
        assertEquals(listOf("newer", "older"), pendingPaymentRows(s).map { it.transactionId })
        assertTrue(pendingPaymentRows(GivingStatement(year = 2026)).isEmpty())
    }

    @Test
    fun `pending never enters any total`() {
        val settled = listOf(payment(200_000, "p1", "2026-09-05T09:00:00Z"))
        val without = GivingStatement(year = 2026, payments = settled)
        val with = without.copy(pending = listOf(pending("t9", amount = 100_000), pending("t10", amount = 50_000, method = "card")))
        val pledges = listOf(monthly(id = "p1", amount = 200_000, dueDay = 5, createdAt = "2026-01-01T00:00:00Z"))

        // The summary (local rule), the months, their subtotals and the foot are the settled money only.
        assertEquals(partnerStatementSummary(2026, without, pledges), partnerStatementSummary(2026, with, pledges))
        assertEquals(200_000, partnerStatementSummary(2026, with, pledges).paidMinor)
        assertEquals(paymentsByMonth(without.payments), paymentsByMonth(with.payments))
        assertEquals(200_000, statementYearTotal(paymentsByMonth(with.payments)))
        assertEquals(
            partnerStatementPledges(2026, without, pledges, today),
            partnerStatementPledges(2026, with, pledges, today),
        )
        // …while the rows themselves are still there to be seen.
        assertEquals(2, pendingPaymentRows(with).size)
    }
}
