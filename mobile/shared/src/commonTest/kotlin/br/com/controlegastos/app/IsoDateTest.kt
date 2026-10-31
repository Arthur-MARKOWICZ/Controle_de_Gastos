package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IsoDateTest {
    @Test
    fun `formats iso dates for display in Brazil`() {
        assertEquals("31/10/2026", formatIsoDateBr("2026-10-31"))
    }

    @Test
    fun `returns the previous calendar day`() {
        assertEquals("2026-10-30", previousIsoDay("2026-10-31"))
        assertEquals("2026-09-30", previousIsoDay("2026-10-01"))
        assertEquals("2025-12-31", previousIsoDay("2026-01-01"))
    }

    @Test
    fun `round-trips utc millis used by the date picker`() {
        val iso = "2026-03-15"
        assertEquals(iso, utcMillisToIsoDate(isoDateToUtcMillis(iso)))
        assertTrue(isoDateToUtcMillis("2026-03-15") < isoDateToUtcMillis("2026-03-16"))
    }
}
