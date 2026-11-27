package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Seção compacta de últimos lançamentos na Visão geral (DESIGN.md ux-3a-6d). */
@OptIn(ExperimentalTestApi::class)
class RecentEntriesSectionUiTest {

    @Test
    fun `shows recent items and opens history shortcut`() = runComposeUiTest {
        var openedHistory = false
        val item = HistoryItemView(
            entry = LedgerEntryView(
                id = "entry-1",
                envelopeId = "env-1",
                kind = LedgerKind.EXPENSE,
                amount = Money.fromApiAmount("42.50"),
                occurredAt = "2026-09-10",
                description = null,
            ),
            envelopeName = "Mercado",
            purpose = "LIMIT",
            role = EnvelopeRole.OWNER,
        )
        setContent {
            VerbasTheme(darkTheme = false) {
                RecentEntriesSection(items = listOf(item), onOpenHistory = { openedHistory = true })
            }
        }
        waitForIdle()

        onNodeWithText("Últimos lançamentos").assertExists()
        onNodeWithText("Mercado").assertExists()
        onNodeWithText("R$ 42,50").assertExists()
        onNodeWithText("Ver histórico").performClick()
        assertTrue(openedHistory)
    }

    @Test
    fun `shows empty state without income history`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                RecentEntriesSection(items = emptyList(), onOpenHistory = {})
            }
        }
        waitForIdle()

        onNodeWithText("Nenhum gasto registrado neste mês ainda.").assertExists()
    }
}
