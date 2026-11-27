package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryListContentTest {
    private fun page(items: Int, total: Long) = HistoryPageView(
        items = List(items) { index ->
            HistoryItemView(
                entry = LedgerEntryView(
                    id = "entry-$index",
                    envelopeId = "env-1",
                    kind = LedgerKind.EXPENSE,
                    amount = Money(100),
                    occurredAt = "2026-09-10",
                    description = "Farmácia",
                ),
                envelopeName = "Saúde",
                purpose = "LIMIT",
                role = EnvelopeRole.OWNER,
            )
        },
        page = 0,
        hasNext = false,
        matchedTotal = Money(total),
    )

    @Test
    fun `shows the filtered total only while a filter is active`() {
        val content = historyListContent(page(items = 2, total = 4800), filtersActive = true)

        assertEquals("Total dos resultados: R$ 48,00 (todas as páginas, sem excluídos)", content.resultsTotal)
        assertNull(content.emptyState)
    }

    @Test
    fun `hides the filtered total without filters`() {
        val content = historyListContent(page(items = 2, total = 4800), filtersActive = false)

        assertNull(content.resultsTotal)
        assertNull(content.emptyState)
    }

    @Test
    fun `empty search offers to clear the filters`() {
        val content = historyListContent(page(items = 0, total = 0), filtersActive = true)

        assertEquals("Total dos resultados: R$ 0,00 (todas as páginas, sem excluídos)", content.resultsTotal)
        assertEquals(HistoryEmptyState("Nenhum gasto corresponde aos filtros.", offerClearFilters = true), content.emptyState)
    }

    @Test
    fun `empty period keeps the period message without clearing`() {
        val content = historyListContent(page(items = 0, total = 0), filtersActive = false)

        assertEquals(HistoryEmptyState("Nenhum lançamento neste período.", offerClearFilters = false), content.emptyState)
    }

    @Test
    fun `groups history items by day newest first`() {
        fun item(id: String, day: String) = HistoryItemView(
            entry = LedgerEntryView(
                id = id,
                envelopeId = "env-1",
                kind = LedgerKind.EXPENSE,
                amount = Money(100),
                occurredAt = day,
                description = null,
            ),
            envelopeName = "Saúde",
            purpose = "LIMIT",
            role = EnvelopeRole.OWNER,
        )
        val grouped = groupHistoryByDay(
            listOf(item("a", "2026-09-10"), item("b", "2026-09-12"), item("c", "2026-09-10")),
        )

        assertEquals(listOf("2026-09-12", "2026-09-10"), grouped.map { it.first })
        assertEquals(listOf("a", "c"), grouped[1].second.map { it.entry.id })
    }

    @Test
    fun `day headings use Hoje Ontem or date`() {
        assertEquals("Hoje · 15/09/2026", formatHistoryDayHeading("2026-09-15", "2026-09-15"))
        assertEquals("Ontem · 14/09/2026", formatHistoryDayHeading("2026-09-14", "2026-09-15"))
        assertEquals("10/09/2026", formatHistoryDayHeading("2026-09-10", "2026-09-15"))
        assertEquals("Ontem · 31/08/2026", formatHistoryDayHeading("2026-08-31", "2026-09-01"))
        assertEquals("30/08/2026", formatHistoryDayHeading("2026-08-30", "2026-09-01"))
    }

    @Test
    fun `shiftIsoDays covers zero delta and month boundaries`() {
        assertEquals("2026-09-15", shiftIsoDays("2026-09-15", 0))
        assertEquals("2026-09-01", shiftIsoDays("2026-09-08", -7))
        assertEquals("2026-10-01", shiftIsoDays("2026-09-30", 1))
    }
}
