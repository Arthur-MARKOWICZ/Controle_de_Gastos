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
import org.json.JSONException
import org.json.JSONObject

/** Portas HTTP de alerta, compartilhamento, relatório, verba e renda. */
class AndroidApiGatewayTest {

    private val server = FakeApiServer()
    private val store = InMemoryRefreshStore()
    private val client = AndroidApiClient(server.baseUrl, store).apply {
        rememberSession(ApiResponse(200, """{"accessToken": "t1"}""", "refresh_token=r1"))
    }
    private val cacheDir: File = Files.createTempDirectory("relatorios-teste").toFile()
    private val gateway = AndroidApiGateway(client, cacheDir)

    @AfterTest
    fun stop() {
        server.close()
        cacheDir.deleteRecursively()
    }

    private val envelopeJson = """
        {"id": "e-1", "name": "Mercado", "purpose": "LIMIT",
         "baseAmount": {"amount": "500.00", "currency": "BRL"},
         "available": {"amount": "10.50", "currency": "BRL"},
         "isNegative": false, "role": "PARTICIPANT", "version": 3}
    """

    // ------------------------------------------------------------------ alertas

    @Test
    fun `lista alertas ativos sem valores monetarios`() = runBlocking {
        server.on("GET", "/api/v1/alerts", FakeApiServer.Reply(200, """{"items": [
            {"id": "a-1", "type": "NEGATIVE_BALANCE", "envelopeId": "e-1", "envelopeName": "Mercado",
             "referenceMonth": "2026-11", "createdAt": "2026-11-05T18:00:00Z"}]}"""))

        val alerts = gateway.listActiveAlerts()

        assertEquals(InAppAlertView("a-1", "NEGATIVE_BALANCE", "e-1", "Mercado", "2026-11", "2026-11-05T18:00:00Z"), alerts.single())
    }

    @Test
    fun `sessao expirada ao listar alertas apaga a sessao e lanca 401`() = runBlocking {
        server.on("GET", "/api/v1/alerts", FakeApiServer.Reply(401))
        server.on("POST", "/api/v1/auth/refresh", FakeApiServer.Reply(401))

        val error = assertFailsWith<ApiException> { gateway.listActiveAlerts() }

        assertEquals(401, error.status)
        assertTrue(error.isSessionExpired())
        assertNull(store.cookie)
    }

    @Test
    fun `dispensar alerta de outro titular lanca o 404 da API`() = runBlocking {
        server.on("POST", "/api/v1/alerts/a-9/dismiss", FakeApiServer.Reply(404, problem("ALERT_NOT_FOUND", "Alerta não encontrado.")))

        val error = assertFailsWith<ApiException> { gateway.dismissAlert("a-9") }

        assertEquals("ALERT_NOT_FOUND", error.code)
    }

    // --------------------------------------------------------- compartilhamento

    @Test
    fun `convida enviando so o e-mail no corpo`() = runBlocking {
        server.on("POST", "/api/v1/envelopes/e-1/participants", FakeApiServer.Reply(201,
            """{"userId": "u-2", "email": "bruno@example.com", "addedAt": "2026-11-05T18:00:00Z"}"""))

        val participant = gateway.inviteParticipant("e-1", "bruno@example.com")

        assertEquals("u-2", participant.userId)
        assertEquals(setOf("email"), JSONObject(server.requests.single().body).keys().asSequence().toSet())
    }

    @Test
    fun `participante tentando remover outra pessoa recebe 403 FORBIDDEN`() = runBlocking {
        server.on("DELETE", "/api/v1/envelopes/e-1/participants/u-1", FakeApiServer.Reply(403, problem("FORBIDDEN", "")))

        val error = assertFailsWith<ApiException> { gateway.removeParticipant("e-1", "u-1") }

        assertEquals(403, error.status)
        assertEquals("Só quem criou a verba pode fazer isso.", describeFailure(error, "x"))
    }

    // ---------------------------------------------------------------- relatórios

    @Test
    fun `relatorio baixado fica no diretorio privado com o nome sugerido`() = runBlocking {
        server.on("GET", "/api/v1/reports/expenses-by-purpose", FakeApiServer.Reply(200, "tipo;total",
            mapOf("Content-Disposition" to "attachment; filename=\"gastos.csv\"")))

        val report = gateway.downloadReport(ReportId.EXPENSES_BY_PURPOSE, "2026-11-01", "2026-11-30", ReportFormat.CSV)

        assertEquals("gastos.csv", report.fileName)
        assertEquals(File(cacheDir, "relatorios/gastos.csv").absolutePath, report.location)
        assertEquals("tipo;total", File(report.location).readText())
        assertEquals("from=2026-11-01&to=2026-11-30&format=csv", server.requests.single().query)
    }

    @Test
    fun `falha do servidor no relatorio nao grava arquivo`() = runBlocking {
        server.on("GET", "/api/v1/reports/expenses-by-purpose", FakeApiServer.Reply(503, ""))

        val error = assertFailsWith<ApiException> {
            gateway.downloadReport(ReportId.EXPENSES_BY_PURPOSE, "2026-11-01", "2026-11-30", ReportFormat.XLSX)
        }

        assertEquals(503, error.status)
        assertFalse(File(cacheDir, "relatorios").listFiles().orEmpty().any())
    }

    @Test
    fun `le ritmo LIMIT com strings decimais e dia de estouro`() = runBlocking {
        server.on(
            "GET",
            "/api/v1/reports/limit-spending-pace",
            FakeApiServer.Reply(
                200,
                """[{"envelopeId":"e1","name":"Combustível","capacity":"400.00","spent":"200.00","projectedMonthSpend":"620.00","dailyRemainingAllowance":"9.09","projectedOverrunDay":"2026-10-20","month":"2026-10"}]""",
            ),
        )

        val row = gateway.loadLimitSpendingPace("2026-10").single()

        assertEquals("e1", row.envelopeId)
        assertEquals("400.00", row.capacity)
        assertEquals("9.09", row.dailyRemainingAllowance)
        assertEquals("2026-10-20", row.projectedOverrunDay)
        assertEquals("month=2026-10", server.requests.single().query)
    }

    // --------------------------------------------------------------------- verbas

    @Test
    fun `le verbas com centavos exatos e papel de participante`() = runBlocking {
        server.on("GET", "/api/v1/envelopes", FakeApiServer.Reply(200, "[$envelopeJson]"))

        val envelope = gateway.listEnvelopes(YearMonth(2026, 11)).single()

        assertEquals(Money(1050), envelope.available)
        assertEquals(EnvelopeRole.PARTICIPANT, envelope.role)
        assertEquals("month=2026-11", server.requests.single().query)
    }

    @Test
    fun `corpo de verbas fora do contrato falha com JSONException`() = runBlocking {
        server.on("GET", "/api/v1/envelopes", FakeApiServer.Reply(200, """{"items": "não é lista"}"""))

        assertFailsWith<JSONException> { gateway.listEnvelopes(null) }
        Unit
    }

    @Test
    fun `valor com escala errada vindo da API e recusado`() = runBlocking {
        server.on("GET", "/api/v1/envelopes", FakeApiServer.Reply(200, "[${envelopeJson.replace("10.50", "10.5")}]"))

        assertFailsWith<IllegalArgumentException> { gateway.listEnvelopes(null) }
        Unit
    }

    // ---------------------------------------------------------------------- renda

    @Test
    fun `mes sem renda configurada vira nulo`() = runBlocking {
        server.on("GET", "/api/v1/income", FakeApiServer.Reply(404, problem("INCOME_NOT_CONFIGURED", "")))

        assertNull(gateway.loadIncome(YearMonth(2026, 11)))
    }

    @Test
    fun `salva a renda com duas casas e um unico campo`() = runBlocking {
        server.on("PUT", "/api/v1/income", FakeApiServer.Reply(200,
            """{"amount": "5000.00", "effectiveFrom": "2026-11", "changedAt": "2026-11-05T18:00:00Z"}"""))

        val income = gateway.saveIncome(Money(500_000))

        assertEquals(Money(500_000), income.amount)
        assertEquals("""{"amount":"5000.00"}""", server.requests.single().body)
    }

    @Test
    fun `renda abaixo das verbas lanca o codigo da API`() = runBlocking {
        server.on("PUT", "/api/v1/income", FakeApiServer.Reply(422, problem("INCOME_BELOW_BASE_ALLOCATIONS", "Renda insuficiente.")))

        val error = assertFailsWith<ApiException> { gateway.saveIncome(Money(0)) }

        assertEquals("INCOME_BELOW_BASE_ALLOCATIONS", error.code)
        assertEquals("""{"amount":"0.00"}""", server.requests.single().body)
    }
}
