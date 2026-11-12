package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Data proposta ao lançar, derivada do mês que a pessoa está vendo.
 *
 * O mês visível pode não ser o corrente: a data precisa cair nesse mês sem
 * nunca ser futura, e o mês futuro simplesmente não aceita lançamento.
 */
class EntryDateTest {
    private val today = "2026-11-12"

    @Test
    fun `proposes today when the viewed month is the current one`() {
        assertEquals(today, defaultOccurredAt(YearMonth(2026, 11), today))
    }

    @Test
    fun `proposes the last day of a past month`() {
        assertEquals("2026-10-31", defaultOccurredAt(YearMonth(2026, 10), today))
        assertEquals("2026-02-28", defaultOccurredAt(YearMonth(2026, 2), today))
        assertEquals("2024-02-29", defaultOccurredAt(YearMonth(2024, 2), today))
    }

    @Test
    fun `has no valid date for a future month`() {
        assertNull(defaultOccurredAt(YearMonth(2026, 12), today))
        assertNull(defaultOccurredAt(YearMonth(2027, 1), today))
    }

    @Test
    fun `allows registering in the current month and in past months only`() {
        assertTrue(canRegisterIn(YearMonth(2026, 11), today))
        assertTrue(canRegisterIn(YearMonth(2026, 10), today))
        assertTrue(canRegisterIn(YearMonth(2025, 12), today))
        assertFalse(canRegisterIn(YearMonth(2026, 12), today))
        assertFalse(canRegisterIn(YearMonth(2027, 1), today))
    }

    @Test
    fun `accepts only days of the viewed month that already happened`() {
        assertTrue(isSelectableEntryDate("2026-11-01", YearMonth(2026, 11), today))
        assertTrue(isSelectableEntryDate(today, YearMonth(2026, 11), today))
        assertFalse(isSelectableEntryDate("2026-11-13", YearMonth(2026, 11), today))
        assertFalse(isSelectableEntryDate("2026-10-31", YearMonth(2026, 11), today))

        assertTrue(isSelectableEntryDate("2026-10-31", YearMonth(2026, 10), today))
        assertTrue(isSelectableEntryDate("2026-10-01", YearMonth(2026, 10), today))
        assertFalse(isSelectableEntryDate("2026-11-01", YearMonth(2026, 10), today))
        assertFalse(isSelectableEntryDate("", YearMonth(2026, 10), today))
    }

    @Test
    fun `closes the range on today or on the end of the viewed month`() {
        assertEquals(today, lastSelectableEntryDay(YearMonth(2026, 11), today))
        assertEquals("2026-10-31", lastSelectableEntryDay(YearMonth(2026, 10), today))
    }

    @Test
    fun `names the month the sheet registers in when the tab is behind the panel`() {
        // Painel no mês corrente, aba Gastos em "2026-08": a folha aberta pela
        // aba identifica o mês dela, não o do painel.
        val viewed = YearMonth.parse("2026-08")

        assertEquals("Agosto de 2026", viewed.label())
        assertEquals("2026-08-31", defaultOccurredAt(viewed, today))
        assertTrue(isSelectableEntryDate("2026-08-15", viewed, today))
        assertFalse(isSelectableEntryDate(today, viewed, today))
    }

    @Test
    fun `never offers a shortcut to a date the entry cannot have`() {
        assertEquals(
            listOf("Hoje" to today, "Ontem" to "2026-11-11"),
            entryDateShortcuts(YearMonth(2026, 11), today),
        )
        assertEquals(
            listOf("Último dia do mês" to "2026-10-31"),
            entryDateShortcuts(YearMonth(2026, 10), today),
        )
        // Mês futuro não tem data possível: nada é proposto.
        assertEquals(emptyList(), entryDateShortcuts(YearMonth(2026, 12), today))
    }
}
