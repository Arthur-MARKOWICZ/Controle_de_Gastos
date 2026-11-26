package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IsoDateTest {
    @Test
    fun `formats iso dates for display in Brazil`() {
        assertEquals("31/10/2026", formatIsoDateBr("2026-10-31"))
    }

    @Test
    fun `leaves a value that is not an iso date untouched`() {
        // A lista mostra o que vier: formatar não pode quebrar a tela se a
        // data chegar fora do contrato.
        assertEquals("", formatIsoDateBr(""))
        assertEquals("ontem", formatIsoDateBr("ontem"))
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

    @Test
    fun `mes 13 nao e formatado como data`() {
        assertEquals("2026-13-01", formatIsoDateBr("2026-13-01"))
    }

    @Test
    fun `dia anterior a marco em ano bissexto e 29 de fevereiro`() {
        assertEquals("2028-02-29", previousIsoDay("2028-03-01"))
        assertEquals("2026-02-28", previousIsoDay("2026-03-01"))
    }

    @Test
    fun `primeiro dia de 1970 corresponde a zero milissegundos`() {
        assertEquals(0L, isoDateToUtcMillis("1970-01-01"))
        assertEquals("1970-01-01", utcMillisToIsoDate(0L))
    }

    @Test
    fun `dia anterior de data curta falha com erro de indice`() {
        assertFailsWith<IndexOutOfBoundsException> { previousIsoDay("2026-01") }
    }

    @Test
    fun `conversao para milissegundos recusa texto que nao e numero`() {
        assertFailsWith<NumberFormatException> { isoDateToUtcMillis("aaaa-bb-cc") }
    }

    @Test
    fun `virada de ano bissexto preserva o dia 31 de dezembro`() {
        assertEquals("2028-12-31", utcMillisToIsoDate(isoDateToUtcMillis("2028-12-31")))
    }
}
