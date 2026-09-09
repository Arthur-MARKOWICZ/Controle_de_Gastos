package br.com.controlegastos.app

import android.content.Context
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Cliente HTTP do aplicativo.
 *
 * Concentra o que antes era repetido em cada chamada: anexar o access token,
 * renovar a sessão uma vez diante de 401 e traduzir `problem+json` em
 * [ApiException]. O refresh continua sendo enviado como cookie, porque é assim
 * que a API o emite e o aceita.
 */
class AndroidApiClient(
    context: Context,
    private val baseUrl: String,
) {
    private val refreshStore = EncryptedRefreshTokenStore(context)
    private val refreshMutex = Mutex()

    @Volatile
    private var accessToken: String? = null

    val hasStoredSession: Boolean get() = refreshStore.load() != null

    /**
     * Executa a chamada e, em 401, tenta renovar a sessão uma única vez.
     *
     * @param authenticated anexa o access token corrente.
     * @param bearer sobrepõe o token — usado pelo token restrito do MFA.
     */
    suspend fun request(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        jsonBody: String? = null,
        authenticated: Boolean = true,
        bearer: String? = null,
    ): ApiResponse {
        val token = bearer ?: accessToken.takeIf { authenticated }
        val first = send(method, path, query, jsonBody, token)
        if (first.status != 401 || bearer != null || !authenticated) return first
        if (!refreshSession()) return first
        return send(method, path, query, jsonBody, accessToken)
    }

    suspend fun requestJson(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        jsonBody: String? = null,
        authenticated: Boolean = true,
        bearer: String? = null,
        failure: String,
    ): JSONObject {
        val response = request(method, path, query, jsonBody, authenticated, bearer).orThrow(failure)
        return if (response.body.isBlank()) JSONObject() else JSONObject(response.body)
    }

    /** Baixa um corpo binário, como os relatórios em CSV e XLSX. */
    suspend fun <T> download(
        path: String,
        query: Map<String, String?>,
        failure: String,
        consume: (fileName: String?, stream: InputStream) -> T,
    ): T = withContext(Dispatchers.IO) {
        var attempt = open("GET", path, query, null, accessToken)
        try {
            if (attempt.responseCode == 401) {
                attempt.disconnect()
                if (!refreshSession()) throw ApiException(401, "", failure)
                attempt = open("GET", path, query, null, accessToken)
            }
            val status = attempt.responseCode
            if (status !in 200..299) {
                val body = attempt.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val problem = ApiResponse(status, body, null)
                throw ApiException(status, problem.problemCode(), problem.problemDetail().ifBlank { failure })
            }
            val fileName = fileNameOf(attempt.getHeaderField("Content-Disposition"))
            attempt.inputStream.use { consume(fileName, it) }
        } finally {
            attempt.disconnect()
        }
    }

    fun rememberSession(response: ApiResponse) {
        accessToken = JSONObject(response.body).getString("accessToken")
        response.refreshCookie?.let { cookie ->
            if (cookie.substringAfter('=', "").isBlank()) refreshStore.clear() else refreshStore.save(cookie)
        }
    }

    fun clearSession() {
        accessToken = null
        refreshStore.clear()
    }

    /** Renova a sessão uma vez por vez; chamadas concorrentes reaproveitam o resultado. */
    suspend fun refreshSession(): Boolean {
        val observed = accessToken
        return refreshMutex.withLock {
            if (accessToken != null && accessToken != observed) return@withLock true
            val response = send("POST", "/api/v1/auth/refresh", emptyMap(), null, null)
            if (response.status !in 200..299) {
                clearSession()
                return@withLock false
            }
            rememberSession(response)
            true
        }
    }

    private suspend fun send(
        method: String,
        path: String,
        query: Map<String, String?>,
        jsonBody: String?,
        bearer: String?,
    ): ApiResponse = withContext(Dispatchers.IO) {
        val connection = open(method, path, query, jsonBody, bearer)
        try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            ApiResponse(status, body, refreshCookieOf(connection))
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        method: String,
        path: String,
        query: Map<String, String?>,
        jsonBody: String?,
        bearer: String?,
    ): HttpURLConnection = (URL(baseUrl + path + queryString(query)).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 10_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/json")
        bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
        // O cookie de refresh tem Path=/api/v1/auth e só é aceito nessas rotas.
        if (path.startsWith("/api/v1/auth/")) refreshStore.load()?.let { setRequestProperty("Cookie", it) }
        if (jsonBody != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun queryString(query: Map<String, String?>): String {
        val entries = query.filterValues { !it.isNullOrBlank() }
        if (entries.isEmpty()) return ""
        return entries.entries.joinToString("&", prefix = "?") { (key, value) ->
            "${encode(key)}=${encode(value!!)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun refreshCookieOf(connection: HttpURLConnection): String? = connection.headerFields.entries
        .firstOrNull { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
        ?.value
        ?.firstOrNull { it.startsWith("refresh_token=") || it.startsWith("__Secure-refresh_token=") }
        ?.substringBefore(';')

    private fun fileNameOf(disposition: String?): String? {
        val header = disposition ?: return null
        val encoded = Regex("filename\\*=(?:UTF-8'')?([^;\\s]+)", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
            ?: Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
            ?: return null
        return runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
    }
}

/** Falha a chamada quando a resposta não for 2xx, preservando o `code` da API. */
fun ApiResponse.orThrow(fallback: String): ApiResponse {
    if (status in 200..299) return this
    throw ApiException(status, problemCode(), problemDetail().ifBlank { fallback })
}

data class ApiResponse(val status: Int, val body: String, val refreshCookie: String?) {
    fun problemCode(): String = runCatching { JSONObject(body).optString("code", "") }.getOrDefault("")

    fun problemDetail(): String = runCatching {
        val problem = JSONObject(body)
        problem.optString("detail", "").ifBlank { problem.optString("title", "") }
    }.getOrDefault("")
}
