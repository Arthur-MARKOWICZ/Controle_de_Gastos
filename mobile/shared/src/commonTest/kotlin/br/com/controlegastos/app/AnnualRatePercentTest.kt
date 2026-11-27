package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnnualRatePercentTest {
    @Test
    fun `converts brazilian percent with comma to api decimal scale 4`() {
        assertEquals("0.1250", parseAnnualRatePercentToDecimal("12,50"))
    }

    @Test
    fun `accepts dot as decimal separator`() {
        assertEquals("0.1250", parseAnnualRatePercentToDecimal("12.5"))
    }

    @Test
    fun `rejects blank input`() {
        assertNull(parseAnnualRatePercentToDecimal(""))
        assertNull(parseAnnualRatePercentToDecimal("   "))
    }

    @Test
    fun `rejects zero`() {
        assertNull(parseAnnualRatePercentToDecimal("0"))
        assertNull(parseAnnualRatePercentToDecimal("0,0"))
    }

    @Test
    fun `rejects negative`() {
        assertNull(parseAnnualRatePercentToDecimal("-1"))
        assertNull(parseAnnualRatePercentToDecimal("-12,50"))
    }

    @Test
    fun `strips percent sign and rejects values above one thousand`() {
        assertEquals("0.1250", parseAnnualRatePercentToDecimal("12,50%"))
        assertNull(parseAnnualRatePercentToDecimal("1000,1"))
        assertNull(parseAnnualRatePercentToDecimal("1001"))
        assertEquals("10.0000", parseAnnualRatePercentToDecimal("1000"))
    }

    @Test
    fun `rejects overly long fractional part and rounds to scale 4`() {
        assertNull(parseAnnualRatePercentToDecimal("1." + "1".repeat(13)))
        assertEquals("0.1235", parseAnnualRatePercentToDecimal("12.3456"))
        assertEquals("0.1234", parseAnnualRatePercentToDecimal("12.3444"))
    }

    @Test
    fun `accepts fractional only under one percent and exact scale four`() {
        assertEquals("0.0001", parseAnnualRatePercentToDecimal("0.01"))
        assertEquals("0.1250", parseAnnualRatePercentToDecimal("12.50"))
        assertEquals("1.0000", parseAnnualRatePercentToDecimal("100"))
        assertNull(parseAnnualRatePercentToDecimal("abc"))
        assertNull(parseAnnualRatePercentToDecimal("12,50,00"))
    }
}
