package br.com.controlegastos.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class AndroidInvestmentsGatewayTest {

    private val server = FakeApiServer()
    private val store = InMemoryRefreshStore()
    private val client = AndroidApiClient(server.baseUrl, store).apply {
        rememberSession(ApiResponse(200, """{"accessToken": "t1"}""", "refresh_token=r1"))
    }
    private val cacheDir: File = Files.createTempDirectory("carteira-teste").toFile()
    private val gateway = AndroidApiGateway(client, cacheDir)

    @AfterTest
    fun stop() {
        server.close()
        cacheDir.deleteRecursively()
    }

    @Test
    fun `le summary completo com acoes, lotes e CDBs mantendo dinheiro em centavos exatos`() = runBlocking {
        server.on(
            "GET",
            "/api/v1/investments/summary",
            FakeApiServer.Reply(
                200,
                """
                {
                  "complete": true,
                  "totalCost": {"amount": "11500.00", "currency": "BRL"},
                  "totalCurrent": {"amount": "12900.00", "currency": "BRL"},
                  "totalPnl": {"amount": "1400.00", "currency": "BRL"},
                  "stocks": [
                    {
                      "ticker": "PETR4",
                      "quantity": 100,
                      "averagePrice": {"amount": "15.00", "currency": "BRL"},
                      "acquisitionCost": {"amount": "1500.00", "currency": "BRL"},
                      "currentValue": {"amount": "1650.00", "currency": "BRL"},
                      "pnl": {"amount": "150.00", "currency": "BRL"},
                      "lots": [
                        {
                          "id": "lot-1",
                          "quantity": 100,
                          "unitPrice": {"amount": "15.00", "currency": "BRL"},
                          "acquisitionCost": {"amount": "1500.00", "currency": "BRL"},
                          "purchaseDate": "2026-01-15",
                          "currentValue": {"amount": "1650.00", "currency": "BRL"},
                          "pnl": {"amount": "150.00", "currency": "BRL"}
                        }
                      ]
                    }
                  ],
                  "cdbs": [
                    {
                      "id": "cdb-1",
                      "principal": {"amount": "10000.00", "currency": "BRL"},
                      "estimatedValue": {"amount": "11250.00", "currency": "BRL"},
                      "pnl": {"amount": "1250.00", "currency": "BRL"}
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val summary = gateway.loadSummary()

        assertTrue(summary.complete)
        assertEquals(Money(1_150_000), summary.totalCost)
        assertEquals(Money(1_290_000), summary.totalCurrent)
        assertEquals(Money(140_000), summary.totalPnl)
        val stock = summary.stocks.single()
        assertEquals("PETR4", stock.ticker)
        assertEquals(100L, stock.quantity)
        assertEquals(Money(165_000), stock.currentValue)
        assertEquals("lot-1", stock.lots.single().id)
        val cdb = summary.cdbs.single()
        assertEquals("cdb-1", cdb.id)
        assertEquals(Money(1_125_000), cdb.estimatedValue)
    }

    @Test
    fun `le summary parcial com currentValue e pnl nulos em acao sem cotacao`() = runBlocking {
        server.on(
            "GET",
            "/api/v1/investments/summary",
            FakeApiServer.Reply(
                200,
                """
                {
                  "complete": false,
                  "totalCost": {"amount": "1000.00", "currency": "BRL"},
                  "totalCurrent": {"amount": "0.00", "currency": "BRL"},
                  "totalPnl": {"amount": "0.00", "currency": "BRL"},
                  "stocks": [
                    {
                      "ticker": "VALE3",
                      "quantity": 10,
                      "averagePrice": {"amount": "100.00", "currency": "BRL"},
                      "acquisitionCost": {"amount": "1000.00", "currency": "BRL"},
                      "currentValue": null,
                      "pnl": null,
                      "lots": [
                        {
                          "id": "lot-2",
                          "quantity": 10,
                          "unitPrice": {"amount": "100.00", "currency": "BRL"},
                          "acquisitionCost": {"amount": "1000.00", "currency": "BRL"},
                          "purchaseDate": "2026-02-01",
                          "currentValue": null,
                          "pnl": null
                        }
                      ]
                    }
                  ],
                  "cdbs": []
                }
                """.trimIndent(),
            ),
        )

        val summary = gateway.loadSummary()

        assertFalse(summary.complete)
        assertNull(summary.stocks.single().currentValue)
        assertNull(summary.stocks.single().pnl)
        assertNull(summary.stocks.single().lots.single().currentValue)
    }

    @Test
    fun `cadastra lote de acao enviando unitPrice em string de duas casas`() = runBlocking {
        server.on(
            "POST",
            "/api/v1/investments/stocks",
            FakeApiServer.Reply(
                201,
                """
                {
                  "id": "lot-1",
                  "ticker": "PETR4",
                  "quantity": 100,
                  "unitPrice": {"amount": "10.00", "currency": "BRL"},
                  "acquisitionCost": {"amount": "1000.00", "currency": "BRL"},
                  "purchaseDate": "2026-01-15",
                  "createdAt": "2026-01-15T12:00:00Z",
                  "currentQuote": null,
                  "currentValue": null
                }
                """.trimIndent(),
            ),
        )

        val created = gateway.registerStockLot(NewStockLot("PETR4", 100, Money(1_000), "2026-01-15"))

        assertEquals("lot-1", created.id)
        val sent = JSONObject(server.requests.single().body)
        assertEquals("PETR4", sent.getString("ticker"))
        assertEquals(100L, sent.getLong("quantity"))
        assertEquals("10.00", sent.getJSONObject("unitPrice").getString("amount"))
        assertEquals("BRL", sent.getJSONObject("unitPrice").getString("currency"))
        assertEquals("2026-01-15", sent.getString("purchaseDate"))
    }

    @Test
    fun `exclui lote de acao e CDB via DELETE`() = runBlocking {
        server.on("DELETE", "/api/v1/investments/stocks/lot-1", FakeApiServer.Reply(204))
        server.on("DELETE", "/api/v1/investments/cdbs/cdb-1", FakeApiServer.Reply(204))

        gateway.deleteStockLot("lot-1")
        gateway.deleteCdb("cdb-1")

        assertEquals(listOf("DELETE /api/v1/investments/stocks/lot-1", "DELETE /api/v1/investments/cdbs/cdb-1"),
            server.requests.map { "${it.method} ${it.path}" })
    }

    @Test
    fun `cadastra CDB enviando principal em string de duas casas e taxa decimal`() = runBlocking {
        server.on(
            "POST",
            "/api/v1/investments/cdbs",
            FakeApiServer.Reply(
                201,
                """
                {
                  "id": "cdb-1",
                  "principal": {"amount": "10000.00", "currency": "BRL"},
                  "annualRate": "0.1250",
                  "startDate": "2026-01-01",
                  "maturityDate": "2027-01-01",
                  "estimatedValue": {"amount": "10100.00", "currency": "BRL"},
                  "createdAt": "2026-01-01T12:00:00Z"
                }
                """.trimIndent(),
            ),
        )

        val created = gateway.registerCdb(NewCdbPosition(Money(1_000_000), "0.1250", "2026-01-01", "2027-01-01"))

        assertEquals("cdb-1", created.id)
        assertEquals(Money(1_010_000), created.estimatedValue)
        val sent = JSONObject(server.requests.single().body)
        assertEquals("10000.00", sent.getJSONObject("principal").getString("amount"))
        assertEquals("0.1250", sent.getString("annualRate"))
    }

    @Test
    fun `recusa summary com valor monetario fora da escala de duas casas`() = runBlocking {
        server.on(
            "GET",
            "/api/v1/investments/summary",
            FakeApiServer.Reply(
                200,
                """
                {
                  "complete": true,
                  "totalCost": {"amount": "10.5", "currency": "BRL"},
                  "totalCurrent": {"amount": "10.50", "currency": "BRL"},
                  "totalPnl": {"amount": "0.00", "currency": "BRL"},
                  "stocks": [],
                  "cdbs": []
                }
                """.trimIndent(),
            ),
        )

        assertFailsWith<IllegalArgumentException> { gateway.loadSummary() }
        Unit
    }
}
