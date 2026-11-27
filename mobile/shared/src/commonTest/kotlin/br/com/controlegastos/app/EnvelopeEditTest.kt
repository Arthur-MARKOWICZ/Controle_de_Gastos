package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvelopeEditTest {
    @Test
    fun `empty when only version is set`() {
        assertTrue(EnvelopeEdit(version = 1).isEmpty)
    }

    @Test
    fun `not empty when any business field is present`() {
        assertFalse(EnvelopeEdit(version = 1, name = "Casa").isEmpty)
        assertFalse(EnvelopeEdit(version = 1, baseAmount = Money(100)).isEmpty)
        assertFalse(EnvelopeEdit(version = 1, targetAmount = Money(200)).isEmpty)
        assertFalse(EnvelopeEdit(version = 1, annualAmount = Money(300)).isEmpty)
        assertFalse(EnvelopeEdit(version = 1, dueMonth = 12).isEmpty)
        assertFalse(EnvelopeEdit(version = 1, dueDay = 15).isEmpty)
        assertFalse(EnvelopeEdit(version = 1, fundingMode = FundingMode.MONTHLY).isEmpty)
        assertFalse(EnvelopeEdit(version = 1, wealthAllocation = true).isEmpty)
        assertFalse(EnvelopeEdit(version = 1, wealthAllocation = false).isEmpty)
    }
}
