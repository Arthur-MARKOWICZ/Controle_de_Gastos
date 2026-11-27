package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regra do [DailyReminderWorker] (spec ui-mobile/lembrete-diario).
 * A lógica fica pura para rodar em `testDebugUnitTest` sem Robolectric /
 * `TestListenableWorkerBuilder` (o android.jar da JVM não executa WorkManager).
 */
class DailyReminderWorkerLogicTest {

    @Test
    fun `sem horario nao notifica nem reagenda`() {
        val plan = DailyReminderWorkerLogic.plan(
            reminderTime = null,
            hasStoredSession = true,
            hasExpenseToday = false,
            canNotify = true,
        )

        assertFalse(plan.shouldNotify)
        assertFalse(plan.shouldReschedule)
    }

    @Test
    fun `sem sessao nao notifica nem reagenda`() {
        val plan = DailyReminderWorkerLogic.plan(
            reminderTime = "20:00",
            hasStoredSession = false,
            hasExpenseToday = false,
            canNotify = true,
        )

        assertFalse(plan.shouldNotify)
        assertFalse(plan.shouldReschedule)
    }

    @Test
    fun `sem rede ou falha na consulta nao notifica e reagenda`() {
        val plan = DailyReminderWorkerLogic.plan(
            reminderTime = "20:00",
            hasStoredSession = true,
            hasExpenseToday = null,
            canNotify = true,
        )

        assertFalse(plan.shouldNotify)
        assertTrue(plan.shouldReschedule)
    }

    @Test
    fun `ja lancou hoje nao notifica e reagenda`() {
        val plan = DailyReminderWorkerLogic.plan(
            reminderTime = "20:00",
            hasStoredSession = true,
            hasExpenseToday = true,
            canNotify = true,
        )

        assertFalse(plan.shouldNotify)
        assertTrue(plan.shouldReschedule)
    }

    @Test
    fun `sem gasto e com permissao notifica e reagenda`() {
        val plan = DailyReminderWorkerLogic.plan(
            reminderTime = "20:00",
            hasStoredSession = true,
            hasExpenseToday = false,
            canNotify = true,
        )

        assertTrue(plan.shouldNotify)
        assertTrue(plan.shouldReschedule)
    }

    @Test
    fun `sem gasto mas sem permissao nao notifica e reagenda`() {
        val plan = DailyReminderWorkerLogic.plan(
            reminderTime = "20:00",
            hasStoredSession = true,
            hasExpenseToday = false,
            canNotify = false,
        )

        assertFalse(plan.shouldNotify)
        assertTrue(plan.shouldReschedule)
    }

    @Test
    fun `toque na notificacao usa o extra de lancamento rapido`() {
        assertEquals("br.com.controlegastos.QUICK_EXPENSE", DailyReminderWorkerLogic.QUICK_EXPENSE_EXTRA)
    }
}
