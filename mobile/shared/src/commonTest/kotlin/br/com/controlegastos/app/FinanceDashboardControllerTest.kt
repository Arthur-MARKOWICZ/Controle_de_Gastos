package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val SEPTEMBER = YearMonth(2026, 9)

class FinanceDashboardControllerTest {
    @Test
    fun `uses the server goal percentage for a contribution goal progress bar`() {
        val goal = EnvelopeView(
            id = "goal-1",
            name = "Investimentos",
            purpose = "GOAL",
            baseAmount = Money.fromApiAmount("100.00"),
            available = Money.fromApiAmount("120.00"),
            isNegative = false,
            goalProgress = GoalProgressView(
                Money.fromApiAmount("200.00"),
                Money.fromApiAmount("20.00"),
                Money.fromApiAmount("180.00"),
                10,
            ),
        )

        assertEquals(0.1f, progressOf(goal))
    }

    @Test
    fun `loads the financial dashboard from the gateway`() = runSuspend {
        val gateway = FakeLedgerGateway(dashboard())
        val controller = FinanceDashboardController(gateway, SEPTEMBER)

        controller.refresh()

        assertEquals(DashboardState.Content(dashboard()), controller.state)
    }

    @Test
    fun `omits the month while showing the current one so the server resolves the timezone`() = runSuspend {
        val gateway = FakeLedgerGateway(dashboard())
        val controller = FinanceDashboardController(gateway, SEPTEMBER)

        controller.refresh()

        assertNull(gateway.lastMonth)
        assertEquals(SEPTEMBER, controller.month)
    }

    @Test
    fun `sends the month explicitly once the user navigates away from today`() = runSuspend {
        val gateway = FakeLedgerGateway(dashboard())
        val controller = FinanceDashboardController(gateway, SEPTEMBER)

        controller.showPreviousMonth()

        assertEquals(YearMonth(2026, 8), controller.month)
        assertEquals(YearMonth(2026, 8), gateway.lastMonth)
    }

    @Test
    fun `explains a rejected allocation instead of showing a generic error`() = runSuspend {
        val gateway = FakeLedgerGateway(dashboard())
        gateway.failure = ApiException(409, ApiErrorCode.ALLOCATION_EXCEEDS_INCOME, "")
        val controller = FinanceDashboardController(gateway, SEPTEMBER)

        controller.refresh()

        val state = controller.state as DashboardState.Error
        assertTrue(state.message.contains("passaria da sua renda"), state.message)
    }

    @Test
    fun `reloads the month after registering an entry`() = runSuspend {
        val gateway = FakeLedgerGateway(dashboard())
        val controller = FinanceDashboardController(gateway, SEPTEMBER)
        controller.refresh()

        val reached = controller.registerEntry(
            envelopeId = "envelope-1",
            entry = NewLedgerEntry(LedgerKind.EXPENSE, Money.fromApiAmount("10.00"), "2026-09-09"),
        )

        assertEquals(1, gateway.createdEntries.size)
        assertEquals(2, gateway.loads)
        assertTrue(!reached)
    }

    @Test
    fun `reports when a contribution just reached the target`() = runSuspend {
        val gateway = FakeLedgerGateway(dashboard())
        gateway.targetJustReached = true
        val controller = FinanceDashboardController(gateway, SEPTEMBER)

        val reached = controller.registerEntry(
            envelopeId = "goal-1",
            entry = NewLedgerEntry(LedgerKind.CONTRIBUTION, Money.fromApiAmount("50.00"), "2026-09-09"),
        )

        assertTrue(reached)
    }

    private fun dashboard() = FinancialDashboard(
        income = IncomeView(Money.fromApiAmount("5000.00"), "2026-09", "2026-09-01T00:00:00Z"),
        allocated = Money.fromApiAmount("4250.00"),
        unallocated = Money.fromApiAmount("750.00"),
        usagePct = 85.0,
        envelopes = listOf(
            EnvelopeView(
                id = "envelope-1",
                name = "Combustível",
                purpose = "LIMIT",
                baseAmount = Money.fromApiAmount("400.00"),
                available = Money.fromApiAmount("240.00"),
                isNegative = false,
            ),
        ),
    )
}

private class FakeLedgerGateway(private val dashboard: FinancialDashboard) : LedgerGateway {
    var lastMonth: YearMonth? = null
    var loads = 0
    var failure: Throwable? = null
    var targetJustReached = false
    val createdEntries = mutableListOf<NewLedgerEntry>()

    override suspend fun loadDashboard(month: YearMonth?): FinancialDashboard {
        failure?.let { throw it }
        lastMonth = month
        loads++
        return dashboard
    }

    override suspend fun createEntry(envelopeId: String, entry: NewLedgerEntry): LedgerEntryView {
        createdEntries += entry
        return LedgerEntryView(
            id = "entry-1",
            envelopeId = envelopeId,
            kind = entry.kind,
            amount = entry.amount,
            occurredAt = entry.occurredAt,
            description = entry.description,
            targetJustReached = targetJustReached,
        )
    }

    override suspend fun listEntries(envelopeId: String, month: YearMonth?): List<LedgerEntryView> = emptyList()
    override suspend fun loadHistory(from: String, to: String, page: Int, includeDeleted: Boolean) =
        HistoryPageView(emptyList(), 0, false)
    override suspend fun loadHistorySummary(from: String, to: String) = HistorySummaryView(
        Money(0), Money(0), Money(0), Money(0), emptyList(), emptyList(),
    )
    override suspend fun updateEntry(id: String, edit: LedgerEntryEdit): LedgerEntryView = error("não usado")
    override suspend fun deleteEntry(id: String) = error("não usado")
}
