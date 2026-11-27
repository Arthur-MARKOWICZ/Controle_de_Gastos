package br.com.controlegastos.app

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickExpenseIntentExtrasTest {

    @Test
    fun `extras de lancamento rapido usam o contrato do lembrete e do widget`() {
        assertEquals("br.com.controlegastos.QUICK_EXPENSE", MainActivity.EXTRA_QUICK_EXPENSE)
        assertEquals("br.com.controlegastos.ENVELOPE_ID", MainActivity.EXTRA_ENVELOPE_ID)
        assertEquals(MainActivity.EXTRA_QUICK_EXPENSE, DailyReminderWorkerLogic.QUICK_EXPENSE_EXTRA)
    }
}
