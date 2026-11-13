package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Telas Compose de recorrência (spec ui-mobile/recurring-entries). A regra fica
 * nos controllers; aqui conferimos o que a pessoa vê e toca.
 */
@OptIn(ExperimentalTestApi::class)
class RecurringScreensUiTest {

    private val moradia = EnvelopeView(
        id = "e-1",
        name = "Moradia",
        purpose = EnvelopePurpose.FIXED.api,
        baseAmount = Money.fromApiAmount("2000.00"),
        available = Money.fromApiAmount("2000.00"),
        isNegative = false,
    )

    private class FakeRecurring : RecurringGateway {
        val created = mutableListOf<RecurringEntryInput>()
        override suspend fun listRecurring(envelopeId: String): List<RecurringEntryView> = emptyList()
        override suspend fun createRecurring(envelopeId: String, input: RecurringEntryInput): RecurringEntryView {
            created += input
            return RecurringEntryView("r-1", envelopeId, input.kind, input.amount, input.dayOfMonth, input.description, true, "2026-12")
        }
        override suspend fun updateRecurring(envelopeId: String, id: String, input: RecurringEntryInput, active: Boolean) =
            error("n/a")
        override suspend fun deleteRecurring(envelopeId: String, id: String) = Unit
        override suspend fun listPendingOccurrences() = PendingOccurrencesView()
        override suspend fun confirmOccurrence(id: String, amount: Money?, occurredAt: String?) = Unit
        override suspend fun discardOccurrence(id: String) = Unit
    }

    private fun pending(id: String, description: String, amount: String, due: String, competence: String) =
        PendingOccurrenceView(id, "e-1", "Moradia", LedgerKind.EXPENSE, description, Money.fromApiAmount(amount), due, competence)

    @Test
    fun `owner creates a recurrence on the phone`() = runComposeUiTest {
        val gateway = FakeRecurring()
        setContent {
            VerbasTheme(darkTheme = false) { RecurringEntriesDialog(moradia, gateway, onNotify = {}, onDismiss = {}) }
        }

        onNodeWithText("Nenhuma recorrência ainda.").assertExists()
        onNodeWithText("Descrição (opcional)").performTextInput("Aluguel")
        onNodeWithText("Valor sugerido").performTextReplacement("180000")
        onNodeWithText("Dia do mês").performTextInput("5")
        onNodeWithText("Adicionar").performClick()
        waitForIdle()

        assertEquals(RecurringEntryInput(LedgerKind.EXPENSE, Money.fromApiAmount("1800.00"), 5, "Aluguel"), gateway.created.single())
        onNodeWithText("Aluguel").assertExists()
        onNodeWithText("${Money.fromApiAmount("1800.00").toBrl()} · todo dia 5 · Ativa").assertExists()
    }

    @Test
    fun `day 31 shows the short month hint`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) { RecurringEntriesDialog(moradia, FakeRecurring(), onNotify = {}, onDismiss = {}) }
        }

        onNodeWithText("Dia do mês").performTextInput("31")

        onNodeWithText("Em meses mais curtos, a data será o último dia do mês.").assertExists()
    }

    @Test
    fun `home lists pending and overdue with text labels and 48dp actions`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                PendingOccurrencesSection(
                    pending = PendingOccurrencesView(
                        current = listOf(pending("o-1", "Aluguel", "1800.00", "2026-12-05", "2026-12")),
                        overdue = listOf(pending("o-2", "Internet", "99.90", "2026-11-10", "2026-11")),
                    ),
                    error = null,
                    busyId = null,
                    onConfirm = {},
                    onAdjust = {},
                    onDiscard = {},
                )
            }
        }

        onNodeWithText("Pendências do mês").assertExists()
        onNodeWithText("Vencidas").assertExists()
        onNodeWithText("Aluguel").assertExists()
        onNodeWithText("Internet").assertExists()
        listOf("Confirmar Aluguel", "Ajustar Aluguel", "Descartar Internet").forEach {
            onNodeWithContentDescription(it).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `one tap confirm reports the item`() = runComposeUiTest {
        var confirmed: PendingOccurrenceView? = null
        val aluguel = pending("o-1", "Aluguel", "1800.00", "2026-12-05", "2026-12")
        setContent {
            VerbasTheme(darkTheme = false) {
                PendingOccurrencesSection(PendingOccurrencesView(current = listOf(aluguel)), null, null,
                    onConfirm = { confirmed = it }, onAdjust = {}, onDiscard = {})
            }
        }

        onNodeWithContentDescription("Confirmar Aluguel").performClick()

        assertEquals(aluguel, confirmed)
    }

    @Test
    fun `no pending hides the section`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                PendingOccurrencesSection(PendingOccurrencesView(), null, null, {}, {}, {})
            }
        }

        onNodeWithText("Recorrências para confirmar").assertDoesNotExist()
    }
}
