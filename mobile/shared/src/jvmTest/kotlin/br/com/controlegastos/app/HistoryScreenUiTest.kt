package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Histórico mobile alinhado ao DESIGN.md 5A (chips, agrupamento, filtros). */
@OptIn(ExperimentalTestApi::class)
class HistoryScreenUiTest {

    @Test
    fun `shows period chips day heading and toggles advanced filters`() = runComposeUiTest {
        val gateway = RecordingHistoryGateway()
        val controller = HistoryController(gateway, YearMonth(2026, 9))
        val envelopes = RecordingEnvelopeGateway()

        setContent {
            VerbasTheme(darkTheme = false) {
                HistoryScreen(
                    controller = controller,
                    envelopeGateway = envelopes,
                    today = "2026-09-15",
                )
            }
        }
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithText("Este mês").assertExists(); true }.getOrDefault(false)
        }

        onNodeWithText("Este mês").assertExists()
        onNodeWithText("Mês passado").assertExists()
        onNodeWithText("Últimos 7 dias").assertExists()
        onNodeWithText("Personalizado").assertExists()
        onNodeWithText("Hoje · 15/09/2026").assertExists()
        onNodeWithText("Mercado").assertExists()

        onNodeWithText("Filtros").performClick()
        waitForIdle()
        onNodeWithText("Mostrar lançamentos excluídos").assertExists()

        onNodeWithText("Últimos 7 dias").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) {
            gateway.lastPeriod == ("2026-09-09" to "2026-09-15")
        }
        assertEquals("2026-09-09" to "2026-09-15", gateway.lastPeriod)

        onNodeWithText("Personalizado").performClick()
        waitForIdle()
        onNodeWithText("Mostrar lançamentos excluídos").assertExists()
    }
}

private class RecordingEnvelopeGateway : EnvelopeGateway {
    override suspend fun listEnvelopes(month: YearMonth?): List<EnvelopeView> = listOf(
        EnvelopeView(
            id = "env-1",
            name = "Mercado",
            purpose = "LIMIT",
            baseAmount = Money(10_000),
            available = Money(10_000),
            isNegative = false,
        ),
    )

    override suspend fun createEnvelope(envelope: NewEnvelope): EnvelopeView = error("não usado")
    override suspend fun updateEnvelope(id: String, edit: EnvelopeEdit): EnvelopeView = error("não usado")
    override suspend fun archiveEnvelope(id: String) = error("não usado")
}

private class RecordingHistoryGateway : LedgerGateway {
    var lastPeriod: Pair<String, String>? = null

    override suspend fun loadHistory(
        from: String,
        to: String,
        page: Int,
        includeDeleted: Boolean,
        filter: HistoryFilter,
        size: Int,
    ): HistoryPageView {
        lastPeriod = from to to
        return HistoryPageView(
            items = listOf(
                HistoryItemView(
                    entry = LedgerEntryView(
                        id = "e1",
                        envelopeId = "env-1",
                        kind = LedgerKind.EXPENSE,
                        amount = Money(1_250),
                        occurredAt = "2026-09-15",
                        description = null,
                    ),
                    envelopeName = "Mercado",
                    purpose = "LIMIT",
                    role = EnvelopeRole.OWNER,
                ),
            ),
            page = 0,
            hasNext = false,
            matchedTotal = Money(1_250),
        )
    }

    override suspend fun loadHistorySummary(from: String, to: String): HistorySummaryView =
        HistorySummaryView(
            income = Money(5_000),
            expenses = Money(1_250),
            applied = Money(0),
            netBalance = Money(3_750),
            accumulatedBalance = Money(3_750),
            monthlyTotals = emptyList(),
            purposeTotals = listOf(PurposeTotalView("LIMIT", Money(1_250))),
        )

    override suspend fun loadDashboard(month: YearMonth?): FinancialDashboard = error("não usado")
    override suspend fun listEntries(envelopeId: String, month: YearMonth?): List<LedgerEntryView> = error("não usado")
    override suspend fun createEntry(envelopeId: String, entry: NewLedgerEntry): LedgerEntryView = error("não usado")
    override suspend fun updateEntry(id: String, edit: LedgerEntryEdit): LedgerEntryView = error("não usado")
    override suspend fun deleteEntry(id: String) = error("não usado")
    override suspend fun listEntryRevisions(id: String, page: Int) =
        LedgerEntryRevisionPageView(emptyList(), false)
}
