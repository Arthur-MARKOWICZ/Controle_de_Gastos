package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Recorrências e pendências de confirmação no app (ADR-0031). */
class RecurringTest {

    private fun pending(id: String, description: String?, amount: String, due: String, competence: String) =
        PendingOccurrenceView(
            id = id, envelopeId = "e-1", envelopeName = "Moradia", kind = LedgerKind.EXPENSE,
            description = description, suggestedAmount = Money.fromApiAmount(amount), dueDate = due, competence = competence,
        )

    private class FakeGateway(
        var current: List<PendingOccurrenceView> = emptyList(),
        var overdue: List<PendingOccurrenceView> = emptyList(),
        var confirmFailure: Throwable? = null,
    ) : RecurringGateway {
        val confirmed = mutableListOf<Triple<String, Money?, String?>>()
        val discarded = mutableListOf<String>()
        var loads = 0

        override suspend fun listRecurring(envelopeId: String): List<RecurringEntryView> = emptyList()
        override suspend fun createRecurring(envelopeId: String, input: RecurringEntryInput): RecurringEntryView = error("n/a")
        override suspend fun updateRecurring(envelopeId: String, id: String, input: RecurringEntryInput, active: Boolean) =
            error("n/a")
        override suspend fun deleteRecurring(envelopeId: String, id: String) = Unit
        override suspend fun listPendingOccurrences(): PendingOccurrencesView {
            loads++
            return PendingOccurrencesView(current, overdue)
        }
        override suspend fun confirmOccurrence(id: String, amount: Money?, occurredAt: String?) {
            confirmFailure?.let { throw it }
            confirmed += Triple(id, amount, occurredAt)
            current = current.filterNot { it.id == id }
            overdue = overdue.filterNot { it.id == id }
        }
        override suspend fun discardOccurrence(id: String) {
            discarded += id
            current = current.filterNot { it.id == id }
        }
    }

    @Test
    fun `summary line states amount day and status in text`() {
        val entry = RecurringEntryView("r-1", "e-1", LedgerKind.EXPENSE, Money.fromApiAmount("1800.00"), 5, "Aluguel", true, "2026-12")

        assertEquals("${Money.fromApiAmount("1800.00").toBrl()} · todo dia 5 · Ativa", recurringSummary(entry))
        assertEquals("Inativa", recurringSummary(entry.copy(active = false)).substringAfterLast(" · "))
    }

    @Test
    fun `day hint appears only for days 29 to 31`() {
        assertNull(shortMonthHint(28))
        assertNull(shortMonthHint(null))
        assertTrue(shortMonthHint(31)!!.contains("último dia do mês"))
    }

    @Test
    fun `input validation rejects zero amount and days outside 1 to 31`() {
        assertEquals("Informe um valor positivo", validateRecurringInput(Money(0), "5"))
        assertEquals("Escolha um dia entre 1 e 31", validateRecurringInput(Money(1000), "0"))
        assertEquals("Escolha um dia entre 1 e 31", validateRecurringInput(Money(1000), "32"))
        assertEquals("Escolha um dia entre 1 e 31", validateRecurringInput(Money(1000), "dia"))
        assertNull(validateRecurringInput(Money(180000), "31"))
    }

    @Test
    fun `kind follows the envelope purpose`() {
        assertEquals(LedgerKind.CONTRIBUTION, recurringKindFor(EnvelopePurpose.GOAL.api))
        assertEquals(LedgerKind.EXPENSE, recurringKindFor(EnvelopePurpose.FIXED.api))
    }

    @Test
    fun `controller loads pending and overdue separately`() = runSuspend {
        val gateway = FakeGateway(
            current = listOf(pending("o-1", "Aluguel", "1800.00", "2026-12-05", "2026-12")),
            overdue = listOf(pending("o-2", "Internet", "99.90", "2026-11-10", "2026-11")),
        )
        val controller = PendingOccurrencesController(gateway)

        controller.refresh()

        assertEquals(listOf("o-1"), controller.pending.current.map { it.id })
        assertEquals(listOf("o-2"), controller.pending.overdue.map { it.id })
        assertTrue(controller.hasPending)
    }

    @Test
    fun `one tap confirm uses the suggestion and removes the item`() = runSuspend {
        val gateway = FakeGateway(current = listOf(pending("o-1", "Aluguel", "1800.00", "2026-12-05", "2026-12")))
        val controller = PendingOccurrencesController(gateway)
        controller.refresh()

        val ok = controller.confirm("o-1")

        assertTrue(ok)
        assertEquals(listOf(Triple<String, Money?, String?>("o-1", null, null)), gateway.confirmed)
        assertTrue(!controller.hasPending)
        assertNull(controller.error)
    }

    @Test
    fun `adjusted confirm sends amount and date`() = runSuspend {
        val gateway = FakeGateway(current = listOf(pending("o-3", "Energia", "200.00", "2026-12-05", "2026-12")))
        val controller = PendingOccurrencesController(gateway)
        controller.refresh()

        controller.confirm("o-3", Money.fromApiAmount("237.45"), "2026-12-06")

        assertEquals(Triple<String, Money?, String?>("o-3", Money.fromApiAmount("237.45"), "2026-12-06"), gateway.confirmed.single())
    }

    @Test
    fun `failure keeps the item, explains and reloads`() = runSuspend {
        val gateway = FakeGateway(
            current = listOf(pending("o-1", "Aluguel", "1800.00", "2026-12-05", "2026-12")),
            confirmFailure = ApiException(409, "RECURRING_OCCURRENCE_RESOLVED", "A pendência já foi confirmada ou descartada."),
        )
        val controller = PendingOccurrencesController(gateway)
        controller.refresh()

        val ok = controller.confirm("o-1")

        assertTrue(!ok)
        assertEquals("A pendência já foi confirmada ou descartada.", controller.error)
        assertEquals(listOf("o-1"), controller.pending.current.map { it.id })
        assertEquals(2, gateway.loads)
    }

    @Test
    fun `discard removes the item without confirming`() = runSuspend {
        val gateway = FakeGateway(current = listOf(pending("o-1", "Streaming", "39.90", "2026-12-05", "2026-12")))
        val controller = PendingOccurrencesController(gateway)
        controller.refresh()

        controller.discard("o-1")

        assertEquals(listOf("o-1"), gateway.discarded)
        assertTrue(gateway.confirmed.isEmpty())
        assertTrue(!controller.hasPending)
    }

    @Test
    fun `item label falls back to the envelope name`() {
        assertEquals("Moradia", pending("o-1", null, "1.00", "2026-12-05", "2026-12").label)
    }
}
