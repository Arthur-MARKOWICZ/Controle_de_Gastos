package br.com.controlegastos.app

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidAuthGateway(
    context: Context,
    private val baseUrl: String,
) : AuthGateway, FinanceGateway {
    private val refreshStore = EncryptedRefreshTokenStore(context)
    private val refreshMutex = Mutex()
    @Volatile private var accessToken: String? = null

    override suspend fun restore(): AuthUser? {
        if (refreshStore.load() == null) return null
        return runCatching {
            if (refreshSingleFlight()) currentUser(retry = true) else null
        }.getOrNull()
    }

    override suspend fun login(email: String, password: String): AuthUser {
        val result = request("POST", "/api/v1/auth/login", credentials(email, password), includeCookie = false)
        requireSuccess(result, "Não foi possível entrar")
        val body = JSONObject(result.body)
        if (body.optBoolean("mfaRequired", false)) {
            throw MfaRequiredException(body.getString("challengeId"))
        }
        rememberTokens(result)
        return currentUser(retry = true)
    }

    override suspend fun verifyMfa(challengeId: String, code: String): AuthUser {
        val payload = JSONObject().put("challengeId", challengeId).put("code", code).toString()
        val result = request("POST", "/api/v1/auth/mfa/verify", payload, includeCookie = false)
        requireSuccess(result, "Não foi possível confirmar o código")
        rememberTokens(result)
        return currentUser(retry = true)
    }

    override suspend fun verifyRecoveryCode(challengeId: String, recoveryCode: String): String {
        val payload = JSONObject().put("challengeId", challengeId).put("recoveryCode", recoveryCode).toString()
        val result = request("POST", "/api/v1/auth/mfa/recovery", payload, includeCookie = false)
        requireSuccess(result, "Não foi possível usar o código de recuperação")
        return JSONObject(result.body).getString("restrictedToken")
    }

    override suspend fun startMfaEnrollment(password: String, restrictedToken: String?): MfaEnrollmentStart {
        val payload = JSONObject().put("password", password).toString()
        val result = request(
            "POST", "/api/v1/mfa/enroll", payload,
            bearer = restrictedToken ?: accessToken, includeCookie = false,
        )
        requireSuccess(result, "Não foi possível iniciar a configuração do MFA")
        val body = JSONObject(result.body)
        return MfaEnrollmentStart(
            otpauthUri = body.getString("otpauthUri"),
            qrImageDataUri = body.getString("qrImageDataUri"),
            manualEntryKey = body.getString("manualEntryKey"),
            pendingExpiresAt = body.getString("pendingExpiresAt"),
        )
    }

    override suspend fun confirmMfaEnrollment(code: String, restrictedToken: String?): List<String> {
        val payload = JSONObject().put("code", code).toString()
        val result = request(
            "POST", "/api/v1/mfa/enroll/confirm", payload,
            bearer = restrictedToken ?: accessToken, includeCookie = false,
        )
        requireSuccess(result, "Não foi possível confirmar o MFA")
        val codes = JSONObject(result.body).getJSONArray("recoveryCodes")
        return List(codes.length()) { codes.getString(it) }
    }

    override suspend fun disableMfa(password: String) {
        val payload = JSONObject().put("password", password).toString()
        val result = request("POST", "/api/v1/mfa/disable", payload, bearer = accessToken, includeCookie = false)
        requireSuccess(result, "Não foi possível desativar o MFA")
    }

    override suspend fun regenerateRecoveryCodes(password: String): List<String> {
        val payload = JSONObject().put("password", password).toString()
        val result = request(
            "POST", "/api/v1/mfa/recovery-codes", payload,
            bearer = accessToken, includeCookie = false,
        )
        requireSuccess(result, "Não foi possível gerar novos códigos")
        val codes = JSONObject(result.body).getJSONArray("recoveryCodes")
        return List(codes.length()) { codes.getString(it) }
    }

    override suspend fun mfaStatus(): MfaStatus {
        val result = request("GET", "/api/v1/mfa/status", bearer = accessToken)
        requireSuccess(result, "Não foi possível consultar o status do MFA")
        val body = JSONObject(result.body)
        val pendingExpiresAt = if (body.has("pendingExpiresAt") && !body.isNull("pendingExpiresAt")) {
            body.getString("pendingExpiresAt")
        } else {
            null
        }
        return MfaStatus(body.getString("status"), pendingExpiresAt)
    }

    override suspend fun register(email: String, password: String) {
        val result = request("POST", "/api/v1/auth/register", credentials(email, password), includeCookie = false)
        requireSuccess(result, "Não foi possível cadastrar")
    }

    override suspend fun logout() {
        try {
            accessToken?.let { request("POST", "/api/v1/auth/logout", bearer = it) }
        } finally {
            accessToken = null
            refreshStore.clear()
        }
    }

    override suspend fun loadDashboard(): FinancialDashboard {
        val result = request("GET", "/api/v1/ledger/summary", bearer = accessToken)
        if (result.status == 401 && refreshSingleFlight()) return loadDashboard()
        requireSuccess(result, "Não foi possível carregar suas verbas")
        val body = JSONObject(result.body)
        val envelopes = body.getJSONArray("envelopes")
        return FinancialDashboard(
            income = body.optJSONObject("income")?.let { MoneyView(it.getString("amount")) },
            allocated = MoneyView(body.getJSONObject("allocated").getString("amount")),
            unallocated = MoneyView(body.getJSONObject("unallocated").getString("amount")),
            usagePct = body.optDouble("usagePct", 0.0),
            envelopes = List(envelopes.length()) { index ->
                val envelope = envelopes.getJSONObject(index)
                EnvelopeView(
                    id = envelope.getString("id"),
                    name = envelope.getString("name"),
                    purpose = envelope.getString("purpose"),
                    baseAmount = MoneyView(envelope.getJSONObject("baseAmount").getString("amount")),
                    available = MoneyView(envelope.getJSONObject("available").getString("amount")),
                    isNegative = envelope.getBoolean("isNegative"),
                    goalProgress = envelope.optJSONObject("goalProgress")?.let { progress ->
                        GoalProgressView(
                            plannedAmount = MoneyView(progress.getJSONObject("plannedAmount").getString("amount")),
                            contributedAmount = MoneyView(progress.getJSONObject("contributedAmount").getString("amount")),
                            remainingAmount = MoneyView(progress.getJSONObject("remainingAmount").getString("amount")),
                            percent = progress.getInt("percent"),
                        )
                    },
                )
            },
        )
    }

    private suspend fun currentUser(retry: Boolean): AuthUser {
        val result = request("GET", "/api/v1/users/me", bearer = accessToken)
        if (result.status == 401 && retry && refreshSingleFlight()) return currentUser(false)
        requireSuccess(result, "Sessão expirada")
        val body = JSONObject(result.body)
        return AuthUser(body.getString("id"), body.getString("email"), body.getBoolean("emailVerified"))
    }

    private suspend fun refreshSingleFlight(): Boolean {
        val observedToken = accessToken
        return refreshMutex.withLock {
            if (accessToken != null && accessToken != observedToken) return@withLock true
            val result = request("POST", "/api/v1/auth/refresh")
            if (result.status !in 200..299) {
                accessToken = null
                refreshStore.clear()
                return@withLock false
            }
            rememberTokens(result)
            true
        }
    }

    private fun rememberTokens(result: HttpResult) {
        accessToken = JSONObject(result.body).getString("accessToken")
        result.refreshCookie?.let { cookie ->
            if (cookie.substringAfter('=', "").isBlank()) refreshStore.clear() else refreshStore.save(cookie)
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        jsonBody: String? = null,
        bearer: String? = null,
        includeCookie: Boolean = path.startsWith("/api/v1/auth/"),
    ): HttpResult = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (includeCookie) refreshStore.load()?.let { setRequestProperty("Cookie", it) }
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val setCookie = connection.headerFields.entries
                .firstOrNull { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                ?.value?.firstOrNull { it.startsWith("refresh_token=") || it.startsWith("__Secure-refresh_token=") }
                ?.substringBefore(';')
            HttpResult(status, body, setCookie)
        } finally {
            connection.disconnect()
        }
    }

    private fun credentials(email: String, password: String) =
        JSONObject().put("email", email).put("password", password).toString()

    private fun requireSuccess(result: HttpResult, message: String) {
        if (result.status !in 200..299) throw AuthRequestException(result.status, message)
    }

    private data class HttpResult(val status: Int, val body: String, val refreshCookie: String?)
}

class AuthRequestException(val status: Int, message: String) : RuntimeException(message)
