package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpenseSuggestionsTest {

    private val spendable = setOf("env-almoco", "env-mercado", "env-uber")

    private val suggestions = ExpenseSuggestions(
        asOf = "2026-11-27T12:00:00-03:00",
        items = listOf(
            ExpenseSuggestionItem("env-uber", "Uber", Money.fromApiAmount("22.00")),
            ExpenseSuggestionItem("env-almoco", "Almoço", Money.fromApiAmount("32.50")),
            ExpenseSuggestionItem("env-mercado", "Mercado", Money.fromApiAmount("80.00")),
        ),
    )

    @Test
    fun `ranking escolhe a primeira sugestão spendable com dígitos do valor`() {
        val prefill = resolveExpensePrefill(suggestions, forcedEnvelopeId = null, spendableIds = spendable)

        assertEquals("env-uber", prefill.envelopeId)
        assertEquals("2200", prefill.amountDigits)
    }

    @Test
    fun `envelope forçado pelo widget vence o ranking`() {
        val prefill = resolveExpensePrefill(
            suggestions,
            forcedEnvelopeId = "env-mercado",
            spendableIds = spendable,
        )

        assertEquals("env-mercado", prefill.envelopeId)
        assertEquals("8000", prefill.amountDigits)
    }

    @Test
    fun `suggestedAmount nulo deixa dígitos vazios`() {
        val emptyAmount = ExpenseSuggestions(
            asOf = "2026-11-27T12:00:00-03:00",
            items = listOf(ExpenseSuggestionItem("env-almoco", "Almoço", null)),
        )

        val prefill = resolveExpensePrefill(emptyAmount, null, spendable)

        assertEquals("env-almoco", prefill.envelopeId)
        assertEquals("", prefill.amountDigits)
    }

    @Test
    fun `lista vazia ou falha não força verba`() {
        val empty = resolveExpensePrefill(
            ExpenseSuggestions("2026-11-27T12:00:00-03:00", emptyList()),
            null,
            spendable,
        )
        assertNull(empty.envelopeId)
        assertEquals("", empty.amountDigits)

        val missing = resolveExpensePrefill(null, null, spendable)
        assertNull(missing.envelopeId)
    }
}
