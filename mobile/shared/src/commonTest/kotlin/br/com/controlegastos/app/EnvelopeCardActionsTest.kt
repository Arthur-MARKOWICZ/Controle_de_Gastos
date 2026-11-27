package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Composição das ações do card.
 *
 * O card tem uma ação primária e as demais num menu, então o que cada papel
 * enxerga é decidido aqui, fora do Compose, e o `EnvelopeCard` só desenha.
 */
class EnvelopeCardActionsTest {
    private fun envelope(
        purpose: EnvelopePurpose,
        role: EnvelopeRole = EnvelopeRole.OWNER,
        available: String = "100.00",
        targetReachedAt: String? = null,
    ) = EnvelopeView(
        id = "e-1",
        name = "Combustível",
        purpose = purpose.api,
        baseAmount = Money.fromApiAmount("100.00"),
        available = Money.fromApiAmount(available),
        isNegative = Money.fromApiAmount(available).isNegative,
        role = role,
        targetReachedAt = targetReachedAt,
    )

    @Test
    fun `owner of a spending limit registers an expense`() {
        val actions = envelopeCardActions(envelope(EnvelopePurpose.LIMIT))

        assertEquals(EnvelopeAction.REGISTER, actions.primary)
        assertEquals("Registrar", actions.primaryLabel)
        assertEquals(LedgerKind.EXPENSE, actions.primaryKind)
    }

    @Test
    fun `owner of a goal contributes instead of spending`() {
        listOf(EnvelopePurpose.GOAL, EnvelopePurpose.SAVINGS_TARGET).forEach { purpose ->
            val actions = envelopeCardActions(envelope(purpose))

            assertEquals(EnvelopeAction.CONTRIBUTE, actions.primary, purpose.api)
            assertEquals("Aportar", actions.primaryLabel, purpose.api)
            assertEquals(LedgerKind.CONTRIBUTION, actions.primaryKind, purpose.api)
        }
    }

    @Test
    fun `participant of a goal still registers an expense`() {
        val actions = envelopeCardActions(envelope(EnvelopePurpose.GOAL, EnvelopeRole.PARTICIPANT))

        assertEquals(EnvelopeAction.REGISTER, actions.primary)
        assertEquals("Registrar", actions.primaryLabel)
        assertEquals(LedgerKind.EXPENSE, actions.primaryKind)
    }

    @Test
    fun `owner reaches entries participants recurrences edit and archive through the menu`() {
        val actions = envelopeCardActions(envelope(EnvelopePurpose.LIMIT))

        assertEquals(
            listOf(
                EnvelopeAction.ENTRIES,
                EnvelopeAction.PARTICIPANTS,
                EnvelopeAction.RECURRING,
                EnvelopeAction.EDIT,
                EnvelopeAction.ARCHIVE,
            ),
            actions.menu,
        )
        assertFalse(actions.primary in actions.menu)
    }

    @Test
    fun `participant only reaches the entries of the envelope`() {
        val actions = envelopeCardActions(envelope(EnvelopePurpose.LIMIT, EnvelopeRole.PARTICIPANT))

        assertEquals(listOf(EnvelopeAction.ENTRIES), actions.menu)
    }

    @Test
    fun `states the negative balance in words instead of an emoji`() {
        val line = statusLine(envelope(EnvelopePurpose.LIMIT, available = "-3.50"))

        assertTrue(line.contains("negativ", ignoreCase = true), line)
        assertFalse(line.contains("⚠"), line)
    }

    @Test
    fun `states a reached target in words instead of an emoji`() {
        val line = statusLine(
            envelope(EnvelopePurpose.SAVINGS_TARGET, targetReachedAt = "2026-11-10"),
        )

        assertEquals("Meta alcançada", line)
        assertFalse(line.contains("✓"), line)
    }

    @Test
    fun `appends LIMIT spending pace from the API without local recalculation`() {
        val pace = LimitSpendingPaceRow(
            envelopeId = "limit",
            name = "Mercado",
            capacity = "400.00",
            spent = "200.00",
            projectedMonthSpend = "620.00",
            dailyRemainingAllowance = "9.09",
            projectedOverrunDay = "2026-10-20",
            month = "2026-10",
        )
        val limitLine = statusLine(envelope(EnvelopePurpose.LIMIT), pace)
        val goalLine = statusLine(envelope(EnvelopePurpose.GOAL), pace)

        assertTrue(limitLine.contains("Estouro projetado em 20/10/2026"), limitLine)
        assertFalse(goalLine.contains("Estouro projetado"), goalLine)
    }

    @Test
    fun `formats daily allowance when overrun day is absent`() {
        val label = spendingPaceLabel(
            LimitSpendingPaceRow(
                envelopeId = "limit",
                name = "Lazer",
                capacity = "310.00",
                spent = "100.00",
                projectedMonthSpend = "310.00",
                dailyRemainingAllowance = "9.55",
                projectedOverrunDay = null,
                month = "2026-10",
            ),
        )

        assertTrue(label.contains("9,55"), label)
        assertTrue(label.contains("por dia até o fim do mês"), label)
    }
}
