package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Bordas dos auxiliares puros de formulário, data e card (tarefa 6.2). */
class HelpersEdgeCasesTest {

    private fun envelope(purpose: String, available: String = "100.00", goal: GoalProgressView? = null) = EnvelopeView(
        id = "e-1",
        name = "Verba",
        purpose = purpose,
        baseAmount = Money.fromApiAmount("100.00"),
        available = Money.fromApiAmount(available),
        isNegative = available.startsWith("-"),
        goalProgress = goal,
    )

    @Test
    fun `aceita data iso valida`() {
        assertTrue(isIsoDate("2026-02-28"))
    }

    @Test
    fun `recusa mes zero e mes treze`() {
        assertFalse(isIsoDate("2026-00-10"))
        assertFalse(isIsoDate("2026-13-10"))
    }

    @Test
    fun `recusa dia zero e dia 32`() {
        assertFalse(isIsoDate("2026-01-00"))
        assertFalse(isIsoDate("2026-01-32"))
    }

    @Test
    fun `recusa data em formato brasileiro, vazia ou com hora`() {
        assertFalse(isIsoDate("10/01/2026"))
        assertFalse(isIsoDate(""))
        assertFalse(isIsoDate("2026-01-10T10:00"))
    }

    @Test
    fun `mes de competencia vem da data`() {
        assertEquals(YearMonth(2026, 2), monthOf("2026-02-28"))
    }

    @Test
    fun `mes de competencia recusa texto que nao e data com erro de formato numerico`() {
        assertFailsWith<NumberFormatException> { monthOf("ontem-xx-yy") }
    }

    @Test
    fun `mes de competencia recusa data curta com erro de indice`() {
        assertFailsWith<IndexOutOfBoundsException> { monthOf("2026") }
    }

    @Test
    fun `meta mostra quanto falta e limite mostra o saldo`() {
        val goal = GoalProgressView(
            plannedAmount = Money.fromApiAmount("500.00"),
            contributedAmount = Money.fromApiAmount("200.00"),
            remainingAmount = Money.fromApiAmount("300.00"),
            percent = 40,
        )

        assertEquals("Faltam R$ 300,00", headlineAmount(envelope(EnvelopePurpose.GOAL.api, goal = goal)))
        assertEquals("R$ 100,00", headlineAmount(envelope(EnvelopePurpose.LIMIT.api)))
    }

    @Test
    fun `progresso de meta fora do limite nao muda o valor exibido de outra natureza`() {
        val goal = GoalProgressView(Money(0), Money(0), Money.fromApiAmount("50.00"), 0)

        assertEquals("R$ 0,00", headlineAmount(envelope(EnvelopePurpose.FIXED.api, available = "0.00", goal = goal)))
    }

    @Test
    fun `saldo negativo aparece com sinal e duas casas`() {
        assertEquals("-R$ 10,50", headlineAmount(envelope(EnvelopePurpose.LIMIT.api, available = "-10.50")))
    }

    @Test
    fun `so meta e meta de poupanca contam como meta`() {
        assertTrue(isGoal(envelope(EnvelopePurpose.GOAL.api)))
        assertTrue(isGoal(envelope(EnvelopePurpose.SAVINGS_TARGET.api)))
        assertFalse(isGoal(envelope(EnvelopePurpose.LIMIT.api)))
        assertFalse(isGoal(envelope(EnvelopePurpose.ANNUAL_EXPENSE.api)))
        assertFalse(isGoal(envelope("DESCONHECIDA")))
    }
}
