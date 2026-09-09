package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YearMonthTest {
    @Test
    fun `reads the year-month the api uses`() {
        val month = YearMonth.parse("2026-09")
        assertEquals(2026, month.year)
        assertEquals(9, month.month)
        assertEquals("2026-09", month.toApiMonth())
    }

    @Test
    fun `rejects values outside the api format`() {
        listOf("2026-9", "2026", "2026-13", "2026-00", "", "setembro").forEach {
            assertNull(YearMonth.parseOrNull(it), "deveria rejeitar '$it'")
        }
    }

    @Test
    fun `walks to the previous and next month across the year boundary`() {
        assertEquals("2026-08", YearMonth.parse("2026-09").previous().toApiMonth())
        assertEquals("2025-12", YearMonth.parse("2026-01").previous().toApiMonth())
        assertEquals("2026-10", YearMonth.parse("2026-09").next().toApiMonth())
        assertEquals("2027-01", YearMonth.parse("2026-12").next().toApiMonth())
    }

    @Test
    fun `labels the month in portuguese`() {
        assertEquals("Setembro de 2026", YearMonth.parse("2026-09").label())
        assertEquals("Janeiro de 2026", YearMonth.parse("2026-01").label())
        assertEquals("Dezembro de 2025", YearMonth.parse("2025-12").label())
    }

    @Test
    fun `bounds the period of a whole month for history queries`() {
        val month = YearMonth.parse("2026-02")
        assertEquals("2026-02-01", month.firstDay())
        assertEquals("2026-02-28", month.lastDay())
        assertEquals("2024-02-29", YearMonth.parse("2024-02").lastDay())
        assertEquals("2026-01-31", YearMonth.parse("2026-01").lastDay())
        assertEquals("2026-04-30", YearMonth.parse("2026-04").lastDay())
        assertEquals("2100-02-28", YearMonth.parse("2100-02").lastDay())
        assertEquals("2000-02-29", YearMonth.parse("2000-02").lastDay())
    }

    @Test
    fun `orders chronologically`() {
        assertEquals(
            listOf("2025-12", "2026-01", "2026-09"),
            listOf("2026-09", "2025-12", "2026-01").map(YearMonth::parse).sorted().map(YearMonth::toApiMonth),
        )
    }
}
