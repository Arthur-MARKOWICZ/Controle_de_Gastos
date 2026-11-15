package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryControllerTest {
    @Test
    fun `asks for the whole current month by default`() = runSuspend {
        val gateway = FakeHistoryGateway()
        HistoryController(gateway, YearMonth(2026, 2)).refresh()

        assertEquals("2026-02-01" to "2026-02-28", gateway.lastPeriod)
    }

    @Test
    fun `walks to the previous month and restarts the paging`() = runSuspend {
        val gateway = FakeHistoryGateway(hasNext = true)
        val controller = HistoryController(gateway, YearMonth(2026, 1))
        controller.refresh()
        controller.nextPage()
        assertEquals(1, controller.page)

        controller.showPreviousMonth()

        assertEquals(YearMonth(2025, 12), controller.month)
        assertEquals(0, controller.page)
        assertEquals("2025-12-01" to "2025-12-31", gateway.lastPeriod)
    }

    @Test
    fun `does not advance past the last page`() = runSuspend {
        val gateway = FakeHistoryGateway(hasNext = false)
        val controller = HistoryController(gateway, YearMonth(2026, 9))
        controller.refresh()

        controller.nextPage()

        assertEquals(0, controller.page)
    }

    @Test
    fun `reloads after deleting an entry so the totals follow`() = runSuspend {
        val gateway = FakeHistoryGateway()
        val controller = HistoryController(gateway, YearMonth(2026, 9))
        controller.refresh()

        controller.deleteEntry("entry-1")

        assertEquals(listOf("entry-1"), gateway.deleted)
        assertEquals(2, gateway.loads)
    }

    @Test
    fun `search sends the trimmed term and restarts the paging`() = runSuspend {
        val gateway = FakeHistoryGateway(hasNext = true)
        val controller = HistoryController(gateway, YearMonth(2026, 9))
        controller.refresh()
        controller.nextPage()

        controller.search("  farmácia ")

        assertEquals(0, controller.page)
        assertEquals("farmácia", controller.query)
        assertEquals(HistoryFilter(query = "farmácia"), gateway.lastFilter)
        assertTrue(controller.filtersActive)
    }

    @Test
    fun `changing the month keeps the filters`() = runSuspend {
        val gateway = FakeHistoryGateway()
        val controller = HistoryController(gateway, YearMonth(2026, 9))
        controller.search("farmácia")
        controller.filterEnvelope("env-2")

        controller.showPreviousMonth()

        assertEquals("2026-08-01" to "2026-08-31", gateway.lastPeriod)
        assertEquals(HistoryFilter(query = "farmácia", envelopeId = "env-2"), gateway.lastFilter)
    }

    @Test
    fun `filters by envelope and clears every filter`() = runSuspend {
        val gateway = FakeHistoryGateway(hasNext = true)
        val controller = HistoryController(gateway, YearMonth(2026, 9))
        controller.search("farmácia")
        controller.nextPage()

        controller.filterEnvelope("env-2")
        assertEquals(0, controller.page)
        assertEquals("env-2", gateway.lastFilter?.envelopeId)

        controller.clearFilters()

        assertEquals(HistoryFilter(), gateway.lastFilter)
        assertEquals("", controller.query)
        assertEquals(null, controller.envelopeId)
        assertTrue(!controller.filtersActive)
    }

    @Test
    fun `blank search means no text filter`() = runSuspend {
        val gateway = FakeHistoryGateway()
        val controller = HistoryController(gateway, YearMonth(2026, 9))

        controller.search("   ")

        assertEquals(HistoryFilter(), gateway.lastFilter)
        assertTrue(!controller.filtersActive)
    }

    @Test
    fun `explains a rejected edit with the server code`() = runSuspend {
        val gateway = FakeHistoryGateway()
        gateway.failure = ApiException(403, ApiErrorCode.FORBIDDEN, "")
        val controller = HistoryController(gateway, YearMonth(2026, 9))

        controller.refresh()

        val state = controller.state as HistoryState.Error
        assertTrue(state.message.contains("criou a verba"), state.message)
    }
}

private class FakeHistoryGateway(private val hasNext: Boolean = false) : LedgerGateway {
    var lastPeriod: Pair<String, String>? = null
    var lastFilter: HistoryFilter? = null
    var loads = 0
    var failure: Throwable? = null
    val deleted = mutableListOf<String>()

    override suspend fun loadHistory(
        from: String,
        to: String,
        page: Int,
        includeDeleted: Boolean,
        filter: HistoryFilter,
    ): HistoryPageView {
        failure?.let { throw it }
        lastPeriod = from to to
        lastFilter = filter
        loads++
        return HistoryPageView(emptyList(), page, hasNext)
    }

    override suspend fun loadHistorySummary(from: String, to: String): HistorySummaryView {
        failure?.let { throw it }
        return HistorySummaryView(Money(0), Money(0), Money(0), Money(0), Money(0), emptyList(), emptyList())
    }

    override suspend fun deleteEntry(id: String) {
        deleted += id
    }

    override suspend fun loadDashboard(month: YearMonth?): FinancialDashboard = error("não usado")
    override suspend fun listEntries(envelopeId: String, month: YearMonth?): List<LedgerEntryView> = error("não usado")
    override suspend fun createEntry(envelopeId: String, entry: NewLedgerEntry): LedgerEntryView = error("não usado")
    override suspend fun updateEntry(id: String, edit: LedgerEntryEdit): LedgerEntryView = error("não usado")
    override suspend fun listEntryRevisions(id: String, page: Int) =
        LedgerEntryRevisionPageView(emptyList(), false)
}
