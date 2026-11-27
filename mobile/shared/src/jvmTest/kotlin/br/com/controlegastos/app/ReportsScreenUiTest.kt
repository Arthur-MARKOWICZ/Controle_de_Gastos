package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tela de relatórios do app (tarefa 6.3). */
@OptIn(ExperimentalTestApi::class)
class ReportsScreenUiTest {

    private val november = YearMonth(2026, 11)

    private class FakeReports(private val failure: Throwable? = null) : ReportGateway {
        val calls = mutableListOf<List<Any>>()
        override suspend fun downloadReport(report: ReportId, from: String, to: String, format: ReportFormat): DownloadedReport {
            calls += listOf(report, from, to, format)
            failure?.let { throw it }
            return DownloadedReport("gastos.${format.extension}", "/cache/gastos.${format.extension}")
        }

        override suspend fun loadLimitSpendingPace(month: String): List<LimitSpendingPaceRow> = emptyList()
    }

    @Test
    fun `baixa o relatorio do mes inteiro e entrega para compartilhar`() = runComposeUiTest {
        val gateway = FakeReports()
        val opened = mutableListOf<DownloadedReport>()
        val notices = mutableListOf<String>()
        setContent {
            VerbasTheme(darkTheme = false) { ReportsScreen(gateway, november, { opened += it }, { notices += it }) }
        }

        onAllNodesWithText("Baixar XLSX")[0].performClick()
        waitForIdle()

        assertEquals(listOf<Any>(ReportId.EXPENSES_BY_PURPOSE, "2026-11-01", "2026-11-30", ReportFormat.XLSX), gateway.calls.single())
        assertEquals("gastos.xlsx", opened.single().fileName)
        assertEquals(listOf("gastos.xlsx pronto para compartilhar."), notices)
    }

    @Test
    fun `periodo de dois meses em CSV vai do primeiro ao ultimo dia`() = runComposeUiTest {
        val gateway = FakeReports()
        setContent {
            VerbasTheme(darkTheme = false) { ReportsScreen(gateway, november, {}, {}) }
        }

        onAllNodesWithContentDescription("Mês anterior")[0].performClick()
        onNodeWithText(ReportFormat.CSV.label).performClick()
        onAllNodesWithText("Baixar CSV")[0].performClick()
        waitForIdle()

        assertEquals(listOf<Any>(ReportId.EXPENSES_BY_PURPOSE, "2026-10-01", "2026-11-30", ReportFormat.CSV), gateway.calls.single())
    }

    @Test
    fun `periodo invertido avisa e bloqueia o download`() = runComposeUiTest {
        val gateway = FakeReports()
        setContent {
            VerbasTheme(darkTheme = false) { ReportsScreen(gateway, november, {}, {}) }
        }

        onAllNodesWithContentDescription("Próximo mês")[0].performClick()

        onNodeWithText("O mês inicial precisa vir antes do final.").assertExists()
        onAllNodesWithText("Baixar XLSX")[0].assertIsNotEnabled()
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `falha no download nao abre arquivo nem avisa sucesso`() = runComposeUiTest {
        val opened = mutableListOf<DownloadedReport>()
        val notices = mutableListOf<String>()
        setContent {
            VerbasTheme(darkTheme = false) {
                ReportsScreen(FakeReports(IllegalStateException("disco cheio")), november, { opened += it }, { notices += it })
            }
        }

        onAllNodesWithText("Baixar XLSX")[0].performClick()
        waitForIdle()

        assertTrue(opened.isEmpty())
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `falha que nao e da API mostra a mensagem generica`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) { ReportsScreen(FakeReports(IllegalStateException("disco cheio")), november, {}, {}) }
        }

        onAllNodesWithText("Baixar XLSX")[0].performClick()
        waitForIdle()

        onNodeWithText("Não foi possível gerar o relatório.").assertExists()
        onNodeWithText("disco cheio", substring = true).assertDoesNotExist()
    }

    @Test
    fun `sessao expirada pede para entrar novamente`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) { ReportsScreen(FakeReports(ApiException(401, "", "")), november, {}, {}) }
        }

        onAllNodesWithText("Baixar XLSX")[0].performClick()
        waitForIdle()

        onNodeWithText("Sua sessão expirou. Entre novamente.").assertExists()
    }

    @Test
    fun `recusa da API com detalhe mostra o detalhe`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                ReportsScreen(FakeReports(ApiException(400, "INVALID_REQUEST", "Período acima de 12 meses.")), november, {}, {})
            }
        }

        onAllNodesWithText("Baixar XLSX")[0].performClick()
        waitForIdle()

        onNodeWithText("Período acima de 12 meses.").assertExists()
    }
}
