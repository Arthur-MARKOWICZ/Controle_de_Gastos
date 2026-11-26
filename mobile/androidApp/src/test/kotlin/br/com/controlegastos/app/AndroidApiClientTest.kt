package br.com.controlegastos.app

import java.net.ConnectException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class AndroidApiClientTest {

    private val server = FakeApiServer()
    private val store = InMemoryRefreshStore()
    private val client = AndroidApiClient(server.baseUrl, store)

    @AfterTest
    fun stop() = server.close()

    private fun signIn(token: String = "t1", refresh: String = "refresh_token=r1") =
        client.rememberSession(ApiResponse(200, """{"accessToken": "$token"}""", refresh))

    @Test
    fun `anexa o token e manda o cookie de refresh so para as rotas de autenticacao`() = runBlocking {
        server.on("GET", "/api/v1/alerts", FakeApiServer.Reply(200, """{"items": []}"""))
        server.on("POST", "/api/v1/auth/logout", FakeApiServer.Reply(204))
        signIn()

        client.request("GET", "/api/v1/alerts")
        client.request("POST", "/api/v1/auth/logout")

        val alerts = server.requestsTo("GET", "/api/v1/alerts").single()
        assertEquals("Bearer t1", alerts.authorization)
        assertNull(alerts.cookie)
        assertEquals("refresh_token=r1", server.requestsTo("POST", "/api/v1/auth/logout").single().cookie)
    }

    @Test
    fun `renova a sessao uma vez diante de 401 e repete com o novo token`() = runBlocking {
        server.on("GET", "/api/v1/alerts", FakeApiServer.Reply(401), FakeApiServer.Reply(200, """{"items": []}"""))
        server.on(
            "POST", "/api/v1/auth/refresh",
            FakeApiServer.Reply(200, """{"accessToken": "t2"}""", mapOf("Set-Cookie" to "refresh_token=r2; Path=/api/v1/auth; HttpOnly")),
        )
        signIn()

        val response = client.request("GET", "/api/v1/alerts")

        assertEquals(200, response.status)
        assertEquals(listOf("Bearer t1", "Bearer t2"), server.requestsTo("GET", "/api/v1/alerts").map { it.authorization })
        assertEquals("refresh_token=r2", store.cookie)
    }

    @Test
    fun `refresh recusado apaga a sessao e devolve o 401 sem repetir a chamada`() = runBlocking {
        server.on("GET", "/api/v1/alerts", FakeApiServer.Reply(401))
        server.on("POST", "/api/v1/auth/refresh", FakeApiServer.Reply(401))
        signIn()

        val response = client.request("GET", "/api/v1/alerts")

        assertEquals(401, response.status)
        assertNull(store.cookie)
        assertEquals(1, server.requestsTo("GET", "/api/v1/alerts").size)
    }

    @Test
    fun `token restrito recusado nao tenta renovar a sessao`() = runBlocking {
        server.on("POST", "/api/v1/mfa/enroll", FakeApiServer.Reply(401))
        signIn()

        val response = client.request("POST", "/api/v1/mfa/enroll", jsonBody = "{}", bearer = "restrito")

        assertEquals(401, response.status)
        assertEquals("Bearer restrito", server.requests.single().authorization)
        assertEquals(emptyList(), server.requestsTo("POST", "/api/v1/auth/refresh"))
    }

    @Test
    fun `chamada sem autenticacao nunca envia o token guardado`() = runBlocking {
        server.on("POST", "/api/v1/auth/login", FakeApiServer.Reply(401, problem("AUTHENTICATION_FAILED", "")))
        signIn()

        client.request("POST", "/api/v1/auth/login", jsonBody = "{}", authenticated = false)

        assertNull(server.requests.single().authorization)
        assertEquals(emptyList(), server.requestsTo("POST", "/api/v1/auth/refresh"))
    }

    @Test
    fun `cookie de refresh vazio na resposta apaga a sessao guardada`() {
        store.cookie = "refresh_token=antigo"

        client.rememberSession(ApiResponse(200, """{"accessToken": "t1"}""", "refresh_token="))

        assertNull(store.cookie)
    }

    @Test
    fun `problem 422 vira ApiException com codigo e detalhe`() = runBlocking {
        server.on("POST", "/api/v1/envelopes", FakeApiServer.Reply(422, problem("allocation.exceeds_income", "Excede a renda.")))
        signIn()

        val error = assertFailsWith<ApiException> {
            client.requestJson("POST", "/api/v1/envelopes", jsonBody = "{}", failure = "Não foi possível criar a verba")
        }

        assertEquals(422, error.status)
        assertEquals("allocation.exceeds_income", error.code)
        assertEquals("Excede a renda.", error.detail)
    }

    @Test
    fun `erro 500 sem JSON usa a mensagem padrao da chamada`() = runBlocking {
        server.on("GET", "/api/v1/alerts", FakeApiServer.Reply(500, "Internal Server Error"))
        signIn()

        val error = assertFailsWith<ApiException> {
            client.requestJson("GET", "/api/v1/alerts", failure = "Não foi possível carregar os alertas")
        }

        assertEquals(500, error.status)
        assertEquals("", error.code)
        assertEquals("Não foi possível carregar os alertas", error.detail)
    }

    @Test
    fun `servidor fora do ar propaga a falha de conexao`() = runBlocking {
        val offline = AndroidApiClient("http://127.0.0.1:1", InMemoryRefreshStore())

        assertFailsWith<ConnectException> { offline.request("GET", "/api/v1/alerts") }
        Unit
    }

    @Test
    fun `download recusado vira ApiException com o codigo do problem`() = runBlocking {
        server.on("GET", "/api/v1/reports/expenses-by-purpose", FakeApiServer.Reply(403, """{"code": "FORBIDDEN"}"""))
        signIn()

        val error = assertFailsWith<ApiException> {
            client.download("/api/v1/reports/expenses-by-purpose", emptyMap(), "Não foi possível gerar o relatório") { _, _ -> Unit }
        }

        assertEquals(403, error.status)
        assertEquals("FORBIDDEN", error.code)
        assertEquals("Não foi possível gerar o relatório", error.detail)
    }

    @Test
    fun `download le o nome do arquivo em UTF-8 do Content-Disposition`() = runBlocking {
        server.on(
            "GET", "/api/v1/reports/expenses-by-purpose",
            FakeApiServer.Reply(200, "a;b", mapOf("Content-Disposition" to "attachment; filename*=UTF-8''gastos%20de%20novembro.csv")),
        )
        signIn()

        val result = client.download("/api/v1/reports/expenses-by-purpose", mapOf("format" to "csv", "vazio" to " "), "x") { name, stream ->
            name to stream.readBytes().toString(Charsets.UTF_8)
        }

        assertEquals("gastos de novembro.csv" to "a;b", result)
        assertEquals("format=csv", server.requests.single().query)
    }
}
