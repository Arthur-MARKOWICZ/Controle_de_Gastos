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
}
