package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tela Compose da Carteira (tarefas 3.3 e 3.4). */
@OptIn(ExperimentalTestApi::class)
class CarteiraScreenUiTest {

    private class FakeInvestmentsGateway(
        initialSummary: PortfolioSummaryView = emptyPortfolioSummary(),
    ) : InvestmentsGateway {
        var summary: PortfolioSummaryView = initialSummary
        val registeredStocks = mutableListOf<NewStockLot>()
        val deletedStockIds = mutableListOf<String>()
        val registeredCdbs = mutableListOf<NewCdbPosition>()
        val deletedCdbIds = mutableListOf<String>()
        var registerError: Throwable? = null

        override suspend fun loadSummary(): PortfolioSummaryView = summary

        override suspend fun registerStockLot(input: NewStockLot): StockLotView {
            registerError?.let { throw it }
            registeredStocks += input
            val cost = Money(input.unitPrice.cents * input.quantity)
            summary = PortfolioSummaryView(
                complete = true,
                totalCost = cost,
                totalCurrent = cost,
                totalPnl = Money(0),
                stocks = listOf(
                    PortfolioStockPositionView(
                        ticker = input.ticker,
                        quantity = input.quantity,
                        averagePrice = input.unitPrice,
                        acquisitionCost = cost,
                        currentValue = cost,
                        pnl = Money(0),
                        lots = listOf(
                            PortfolioStockLotDetailView(
                                id = "lot-new",
                                quantity = input.quantity,
                                unitPrice = input.unitPrice,
                                acquisitionCost = cost,
                                purchaseDate = input.purchaseDate,
                                currentValue = cost,
                                pnl = Money(0),
                            ),
                        ),
                    ),
                ),
                cdbs = summary.cdbs,
            )
            return StockLotView("lot-new", input.ticker, input.quantity, input.unitPrice, cost, input.purchaseDate, "2026-01-15T12:00:00Z", cost)
        }

        override suspend fun deleteStockLot(id: String) {
            deletedStockIds += id
            summary = emptyPortfolioSummary()
        }

        override suspend fun registerCdb(input: NewCdbPosition): CdbPositionView {
            registerError?.let { throw it }
            registeredCdbs += input
            val estimated = Money(input.principal.cents + 125_000)
            summary = PortfolioSummaryView(
                complete = true,
                totalCost = input.principal,
                totalCurrent = estimated,
                totalPnl = Money(125_000),
                stocks = summary.stocks,
                cdbs = listOf(
                    PortfolioCdbPositionView("cdb-new", input.principal, estimated, Money(125_000)),
                ),
            )
            return CdbPositionView("cdb-new", input.principal, input.annualRate, input.startDate, input.maturityDate, estimated, "2026-01-01T12:00:00Z")
        }

        override suspend fun deleteCdb(id: String) {
            deletedCdbIds += id
            summary = emptyPortfolioSummary()
        }
    }

    @Test
    fun `exibe empty state com acoes para cadastrar acao e CDB quando carteira esta vazia`() = runComposeUiTest {
        val gateway = FakeInvestmentsGateway(emptyPortfolioSummary())
        setContent { VerbasTheme(darkTheme = false) { CarteiraScreen(gateway) } }
        waitForIdle()

        onNodeWithText("Ainda não há posições cadastradas", substring = true).assertExists()
        assertTrue(onAllNodesWithText("Cadastrar ação").fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("Cadastrar CDB").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `exibe totais, acao e CDB rotulado como estimado`() = runComposeUiTest {
        val gateway = FakeInvestmentsGateway(
            PortfolioSummaryView(
                complete = true,
                totalCost = Money(1_150_000),
                totalCurrent = Money(1_290_000),
                totalPnl = Money(140_000),
                stocks = listOf(
                    PortfolioStockPositionView(
                        ticker = "PETR4",
                        quantity = 100,
                        averagePrice = Money(1_500),
                        acquisitionCost = Money(150_000),
                        currentValue = Money(165_000),
                        pnl = Money(15_000),
                        lots = listOf(
                            PortfolioStockLotDetailView("lot-1", 100, Money(1_500), Money(150_000), "2026-01-15", Money(165_000), Money(15_000)),
                        ),
                    ),
                ),
                cdbs = listOf(
                    PortfolioCdbPositionView("cdb-1", Money(1_000_000), Money(1_125_000), Money(125_000)),
                ),
            ),
        )

        setContent { VerbasTheme(darkTheme = false) { CarteiraScreen(gateway) } }
        waitForIdle()

        onNodeWithText("R$ 12.900,00", substring = true).assertExists()
        onNodeWithText("PETR4").assertExists()
        onNodeWithText("R$ 11.250,00", substring = true).assertExists()
        assertTrue(onAllNodesWithText("Estimado", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        onNodeWithText("líquido de IR", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `indica resumo parcial quando complete e false e mostra Sem cotacao`() = runComposeUiTest {
        val gateway = FakeInvestmentsGateway(
            PortfolioSummaryView(
                complete = false,
                totalCost = Money(100_000),
                totalCurrent = Money(0),
                totalPnl = Money(0),
                stocks = listOf(
                    PortfolioStockPositionView(
                        ticker = "VALE3",
                        quantity = 10,
                        averagePrice = Money(10_000),
                        acquisitionCost = Money(100_000),
                        currentValue = null,
                        pnl = null,
                        lots = listOf(
                            PortfolioStockLotDetailView("lot-2", 10, Money(10_000), Money(100_000), "2026-02-01", null, null),
                        ),
                    ),
                ),
                cdbs = emptyList(),
            ),
        )

        setContent { VerbasTheme(darkTheme = false) { CarteiraScreen(gateway) } }
        waitForIdle()

        onNodeWithText("Resumo parcial", substring = true, ignoreCase = true).assertExists()
        onNodeWithText("VALE3").assertExists()
        assertTrue(onAllNodesWithText("Sem cotação", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `cadastra acao pelo dialogo e atualiza o resumo`() = runComposeUiTest {
        val gateway = FakeInvestmentsGateway(emptyPortfolioSummary())
        val notices = mutableListOf<String>()
        setContent { VerbasTheme(darkTheme = false) { CarteiraScreen(gateway, onNotify = { notices += it }) } }
        waitForIdle()

        onAllNodesWithText("Cadastrar ação")[0].performClick()
        waitForIdle()

        onNodeWithText("Ticker B3").performTextInput("PETR4")
        onNodeWithText("Quantidade").performTextInput("100")
        onNodeWithText("Preço unitário").performTextReplacement("1000")
        onNodeWithText("Data de compra").performTextInput("2026-01-15")
        onNodeWithText("Salvar ação").performClick()
        waitForIdle()

        assertEquals(NewStockLot("PETR4", 100, Money(1_000), "2026-01-15"), gateway.registeredStocks.single())
        onNodeWithText("PETR4").assertExists()
        assertTrue(notices.isNotEmpty())
    }

    @Test
    fun `cadastra CDB pelo dialogo e exibe valor rotulado como estimado`() = runComposeUiTest {
        val gateway = FakeInvestmentsGateway(emptyPortfolioSummary())
        setContent { VerbasTheme(darkTheme = false) { CarteiraScreen(gateway) } }
        waitForIdle()

        onAllNodesWithText("Cadastrar CDB")[0].performClick()
        waitForIdle()

        onNodeWithText("Valor aplicado").performTextReplacement("1000000")
        onNodeWithText("Taxa anual (% a.a.)").performTextInput("12,50")
        onNodeWithText("Data de início").performTextInput("2026-01-01")
        onNodeWithText("Data de vencimento").performTextInput("2027-01-01")
        onNodeWithText("Salvar CDB").performClick()
        waitForIdle()

        assertEquals(NewCdbPosition(Money(1_000_000), "0.1250", "2026-01-01", "2027-01-01"), gateway.registeredCdbs.single())
        assertTrue(onAllNodesWithText("Estimado", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `exclui CDB apos confirmacao de risco e atualiza a tela`() = runComposeUiTest {
        val gateway = FakeInvestmentsGateway(
            PortfolioSummaryView(
                complete = true,
                totalCost = Money(1_000_000),
                totalCurrent = Money(1_125_000),
                totalPnl = Money(125_000),
                stocks = emptyList(),
                cdbs = listOf(PortfolioCdbPositionView("cdb-1", Money(1_000_000), Money(1_125_000), Money(125_000))),
            ),
        )
        setContent { VerbasTheme(darkTheme = false) { CarteiraScreen(gateway) } }
        waitForIdle()

        onNodeWithText("Excluir CDB").performClick()
        waitForIdle()
        onNodeWithText("Excluir").performClick()
        waitForIdle()

        assertEquals(listOf("cdb-1"), gateway.deletedCdbIds)
        onNodeWithText("Ainda não há posições cadastradas", substring = true).assertExists()
    }

    @Test
    fun `cancelar exclusao de lote nao chama DELETE e mantem a posicao`() = runComposeUiTest {
        val gateway = FakeInvestmentsGateway(
            PortfolioSummaryView(
                complete = true,
                totalCost = Money(150_000),
                totalCurrent = Money(165_000),
                totalPnl = Money(15_000),
                stocks = listOf(
                    PortfolioStockPositionView(
                        ticker = "PETR4",
                        quantity = 100,
                        averagePrice = Money(1_500),
                        acquisitionCost = Money(150_000),
                        currentValue = Money(165_000),
                        pnl = Money(15_000),
                        lots = listOf(
                            PortfolioStockLotDetailView("lot-1", 100, Money(1_500), Money(150_000), "2026-01-15", Money(165_000), Money(15_000)),
                        ),
                    ),
                ),
                cdbs = emptyList(),
            ),
        )
        setContent { VerbasTheme(darkTheme = false) { CarteiraScreen(gateway) } }
        waitForIdle()

        onNodeWithText("Excluir lote").performClick()
        waitForIdle()
        onNodeWithText("Cancelar").performClick()
        waitForIdle()

        assertTrue(gateway.deletedStockIds.isEmpty())
        onNodeWithText("PETR4").assertExists()
    }

    @Test
    fun `participante de verba alheia gerencia a propria carteira vazia sem seletor de titular`() = runComposeUiTest {
        val gateway = FakeInvestmentsGateway(emptyPortfolioSummary())
        setContent { VerbasTheme(darkTheme = false) { CarteiraScreen(gateway) } }
        waitForIdle()

        // Não oferece carteira de outro titular ou seletor de dono de verba
        onNodeWithText("dono", substring = true, ignoreCase = true).assertDoesNotExist()
        onNodeWithText("participante", substring = true, ignoreCase = true).assertDoesNotExist()

        onAllNodesWithText("Cadastrar ação")[0].performClick()
        waitForIdle()

        onNodeWithText("Ticker B3").performTextInput("ITUB4")
        onNodeWithText("Quantidade").performTextInput("20")
        onNodeWithText("Preço unitário").performTextReplacement("3000")
        onNodeWithText("Data de compra").performTextInput("2026-03-01")
        onNodeWithText("Salvar ação").performClick()
        waitForIdle()

        assertEquals("ITUB4", gateway.registeredStocks.single().ticker)
        onNodeWithText("ITUB4").assertExists()
    }
}

private fun emptyPortfolioSummary() = PortfolioSummaryView(
    complete = true,
    totalCost = Money(0),
    totalCurrent = Money(0),
    totalPnl = Money(0),
    stocks = emptyList(),
    cdbs = emptyList(),
)
