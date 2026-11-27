package br.com.controlegastos.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickExpenseIntentsTest {

    @Test
    fun `estado do widget sem sessao pede login`() {
        assertEquals(
            WidgetSuggestionState.SignedOut,
            WidgetSuggestionLogic.stateFor(authenticated = false, suggestions = null),
        )
    }

    @Test
    fun `estado autenticado limita a tres sugestoes`() {
        val items = (1..5).map {
            ExpenseSuggestionItem("env-$it", "Verba $it", Money.fromApiAmount("10.00"))
        }
        val state = WidgetSuggestionLogic.stateFor(
            authenticated = true,
            suggestions = ExpenseSuggestions("2026-11-27T12:00:00-03:00", items),
        ) as WidgetSuggestionState.Ready

        assertEquals(3, state.items.size)
        assertFalse(state.items.any { it.name == "Verba 4" })
        assertTrue(state.items.first().envelopeId == "env-1")
    }

    @Test
    fun `tile e widget reutilizam o mesmo extra de lancamento rapido`() {
        assertEquals(MainActivity.EXTRA_QUICK_EXPENSE, DailyReminderWorkerLogic.QUICK_EXPENSE_EXTRA)
    }

    @Test
    fun `toque em verba do widget forca envelopeId no deep link`() {
        val link = QuickExpenseDeepLink.forEnvelope("env-mercado")
        assertTrue(link.quickExpense)
        assertEquals("env-mercado", link.envelopeId)
    }

    @Test
    fun `tile e botao generico abrem lancamento sem envelope forçado`() {
        val link = QuickExpenseDeepLink.generic()
        assertTrue(link.quickExpense)
        assertEquals(null, link.envelopeId)
        assertEquals(null, QuickExpenseDeepLink.forEnvelope("  ").envelopeId)
    }

    @Test
    fun `sem sessao o widget pede login e o tile ainda aponta para a MainActivity`() {
        assertEquals(
            WidgetSuggestionState.SignedOut,
            WidgetSuggestionLogic.stateFor(authenticated = false, suggestions = null),
        )
        // Tile/widget usam o mesmo contrato; sem sessão a MainActivity mostra auth.
        val link = QuickExpenseDeepLink.generic()
        assertTrue(link.quickExpense)
    }
}
