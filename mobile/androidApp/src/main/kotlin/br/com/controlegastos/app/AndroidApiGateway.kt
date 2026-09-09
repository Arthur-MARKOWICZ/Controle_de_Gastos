package br.com.controlegastos.app

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Implementação Android de todas as portas do aplicativo.
 *
 * O parsing é manual com `org.json`, como no restante do módulo: o projeto não
 * carrega biblioteca de serialização e os corpos são pequenos e estáveis.
 */
class AndroidApiGateway(
    private val context: Context,
    private val client: AndroidApiClient,
) : AuthGateway, EnvelopeGateway, LedgerGateway, IncomeGateway, ReportGateway {

    // ---------------------------------------------------------------- sessão

    override suspend fun restore(): AuthUser? {
        if (!client.hasStoredSession) return null
        return runCatching { if (client.refreshSession()) currentUser() else null }.getOrNull()
    }

    override suspend fun login(email: String, password: String): AuthUser {
        val response = client
            .request("POST", "/api/v1/auth/login", jsonBody = credentials(email, password), authenticated = false)
            .orThrow("Não foi possível entrar")
        val body = JSONObject(response.body)
        if (body.optBoolean("mfaRequired", false)) throw MfaRequiredException(body.getString("challengeId"))
        client.rememberSession(response)
        return currentUser()
    }

    override suspend fun register(email: String, password: String) {
        client.request("POST", "/api/v1/auth/register", jsonBody = credentials(email, password), authenticated = false)
            .orThrow("Não foi possível cadastrar")
    }

    override suspend fun logout() {
        try {
            client.request("POST", "/api/v1/auth/logout")
        } finally {
            client.clearSession()
        }
    }

    private suspend fun currentUser(): AuthUser {
        val body = client.requestJson("GET", "/api/v1/users/me", failure = "Sessão expirada")
        return AuthUser(body.getString("id"), body.getString("email"), body.getBoolean("emailVerified"))
    }

    // -------------------------------------------------------------------- MFA

    override suspend fun verifyMfa(challengeId: String, code: String): AuthUser {
        val payload = JSONObject().put("challengeId", challengeId).put("code", code).toString()
        val response = client.request("POST", "/api/v1/auth/mfa/verify", jsonBody = payload, authenticated = false)
            .orThrow("Não foi possível confirmar o código")
        client.rememberSession(response)
        return currentUser()
    }

    override suspend fun verifyRecoveryCode(challengeId: String, recoveryCode: String): String {
        val payload = JSONObject().put("challengeId", challengeId).put("recoveryCode", recoveryCode).toString()
        return client.requestJson(
            "POST", "/api/v1/auth/mfa/recovery", jsonBody = payload, authenticated = false,
            failure = "Não foi possível usar o código de recuperação",
        ).getString("restrictedToken")
    }

    override suspend fun startMfaEnrollment(password: String, restrictedToken: String?): MfaEnrollmentStart {
        val body = client.requestJson(
            "POST", "/api/v1/mfa/enroll", jsonBody = JSONObject().put("password", password).toString(),
            bearer = restrictedToken, failure = "Não foi possível iniciar a configuração do MFA",
        )
        return MfaEnrollmentStart(
            otpauthUri = body.getString("otpauthUri"),
            qrImageDataUri = body.getString("qrImageDataUri"),
            manualEntryKey = body.getString("manualEntryKey"),
            pendingExpiresAt = body.getString("pendingExpiresAt"),
        )
    }

    override suspend fun confirmMfaEnrollment(code: String, restrictedToken: String?): List<String> =
        client.requestJson(
            "POST", "/api/v1/mfa/enroll/confirm", jsonBody = JSONObject().put("code", code).toString(),
            bearer = restrictedToken, failure = "Não foi possível confirmar o MFA",
        ).stringList("recoveryCodes")

    override suspend fun disableMfa(password: String) {
        client.request("POST", "/api/v1/mfa/disable", jsonBody = JSONObject().put("password", password).toString())
            .orThrow("Não foi possível desativar o MFA")
    }

    override suspend fun regenerateRecoveryCodes(password: String): List<String> = client.requestJson(
        "POST", "/api/v1/mfa/recovery-codes", jsonBody = JSONObject().put("password", password).toString(),
        failure = "Não foi possível gerar novos códigos",
    ).stringList("recoveryCodes")

    override suspend fun mfaStatus(): MfaStatus {
        val body = client.requestJson("GET", "/api/v1/mfa/status", failure = "Não foi possível consultar o MFA")
        return MfaStatus(body.getString("status"), body.optStringOrNull("pendingExpiresAt"))
    }

    // ------------------------------------------------------- métodos de login

    override suspend fun requestPasswordReset(email: String) {
        client.request(
            "POST", "/api/v1/auth/password-reset-requests",
            jsonBody = JSONObject().put("email", email).toString(), authenticated = false,
        ).orThrow("Não foi possível enviar o e-mail de redefinição")
    }

    override suspend fun loginMethods(): LoginMethods {
        val body = client.requestJson(
            "GET", "/api/v1/auth/login-methods",
            failure = "Não foi possível consultar seus métodos de login",
        )
        return LoginMethods(
            hasPassword = body.getBoolean("hasPassword"),
            linkedProviders = body.stringList("linkedProviders").mapNotNull(OAuthProvider::fromApiOrNull),
        )
    }

    override suspend fun addPassword(password: String) {
        client.request("POST", "/api/v1/auth/password", jsonBody = JSONObject().put("password", password).toString())
            .orThrow("Não foi possível cadastrar a senha")
    }

    override suspend fun unlinkProvider(provider: OAuthProvider) {
        client.request("DELETE", "/api/v1/auth/oauth/${provider.api}")
            .orThrow("Não foi possível desconectar ${provider.label}")
    }

    // ---------------------------------------------------------------- verbas

    override suspend fun listEnvelopes(month: YearMonth?): List<EnvelopeView> {
        val response = client.request("GET", "/api/v1/envelopes", query = monthQuery(month))
            .orThrow("Não foi possível carregar suas verbas")
        return JSONArray(response.body).mapObjects(::readEnvelope)
    }

    override suspend fun createEnvelope(envelope: NewEnvelope): EnvelopeView = readEnvelope(
        client.requestJson(
            "POST", "/api/v1/envelopes", jsonBody = envelope.toJson(),
            failure = "Não foi possível criar a verba",
        ),
    )

    override suspend fun updateEnvelope(id: String, edit: EnvelopeEdit): EnvelopeView {
        require(!edit.isEmpty) { "Nenhuma alteração informada" }
        return readEnvelope(
            client.requestJson(
                "PATCH", "/api/v1/envelopes/$id", jsonBody = edit.toJson(),
                failure = "Não foi possível salvar a verba",
            ),
        )
    }

    override suspend fun archiveEnvelope(id: String) {
        client.request("POST", "/api/v1/envelopes/$id/archive").orThrow("Não foi possível encerrar a verba")
    }

    // ----------------------------------------------------------- lançamentos

    override suspend fun loadDashboard(month: YearMonth?): FinancialDashboard {
        val body = client.requestJson(
            "GET", "/api/v1/ledger/summary", query = monthQuery(month),
            failure = "Não foi possível carregar suas verbas",
        )
        return FinancialDashboard(
            income = body.optJSONObject("income")?.let {
                IncomeView(
                    amount = Money.fromApiAmount(it.getString("amount")),
                    effectiveFrom = it.optString("effectiveFrom", ""),
                    changedAt = it.optString("changedAt", ""),
                )
            },
            allocated = money(body.getJSONObject("allocated")),
            unallocated = money(body.getJSONObject("unallocated")),
            usagePct = body.optDouble("usagePct", 0.0),
            envelopes = body.getJSONArray("envelopes").mapObjects(::readEnvelope),
        )
    }

    override suspend fun listEntries(envelopeId: String, month: YearMonth?): List<LedgerEntryView> =
        client.requestJson(
            "GET", "/api/v1/envelopes/$envelopeId/entries", query = monthQuery(month) + ("size" to "100"),
            failure = "Não foi possível carregar os lançamentos",
        ).getJSONArray("items").mapObjects(::readEntry)

    override suspend fun createEntry(envelopeId: String, entry: NewLedgerEntry): LedgerEntryView = readEntry(
        client.requestJson(
            "POST", "/api/v1/envelopes/$envelopeId/entries", jsonBody = entry.toJson(),
            failure = "Não foi possível registrar o lançamento",
        ),
    )

    override suspend fun loadHistory(from: String, to: String, page: Int, includeDeleted: Boolean): HistoryPageView {
        val body = client.requestJson(
            "GET", "/api/v1/history",
            query = mapOf(
                "from" to from,
                "to" to to,
                "page" to page.toString(),
                "size" to "20",
                "includeDeleted" to includeDeleted.takeIf { it }?.toString(),
            ),
            failure = "Não foi possível carregar o histórico",
        )
        return HistoryPageView(
            items = body.getJSONArray("items").mapObjects { item ->
                HistoryItemView(
                    entry = readEntry(item.getJSONObject("entry")),
                    envelopeName = item.getString("envelopeName"),
                    purpose = item.getString("purpose"),
                    role = role(item.optString("role")),
                )
            },
            page = body.optInt("page", page),
            hasNext = body.optBoolean("hasNext", false),
        )
    }

    override suspend fun loadHistorySummary(from: String, to: String): HistorySummaryView {
        val body = client.requestJson(
            "GET", "/api/v1/history/summary", query = mapOf("from" to from, "to" to to),
            failure = "Não foi possível carregar o resumo do período",
        )
        return HistorySummaryView(
            income = money(body.getJSONObject("income")),
            expenses = money(body.getJSONObject("expenses")),
            netBalance = money(body.getJSONObject("netBalance")),
            accumulatedBalance = money(body.getJSONObject("accumulatedBalance")),
            monthlyTotals = body.getJSONArray("monthlyTotals").mapObjects {
                MonthlyTotalView(it.getString("month"), money(it.getJSONObject("amount")))
            },
            purposeTotals = body.getJSONArray("purposeTotals").mapObjects {
                PurposeTotalView(it.getString("purpose"), money(it.getJSONObject("amount")))
            },
        )
    }

    override suspend fun updateEntry(id: String, edit: LedgerEntryEdit): LedgerEntryView = readEntry(
        client.requestJson(
            "PATCH", "/api/v1/ledger/entries/$id",
            jsonBody = JSONObject()
                .put("envelopeId", edit.envelopeId)
                .put("amount", moneyJson(edit.amount))
                .apply { edit.description?.let { put("description", it) } }
                .toString(),
            failure = "Não foi possível salvar o lançamento",
        ),
    )

    override suspend fun deleteEntry(id: String) {
        client.request("DELETE", "/api/v1/ledger/entries/$id").orThrow("Não foi possível excluir o lançamento")
    }

    // ------------------------------------------------------------------ renda

    override suspend fun loadIncome(month: YearMonth?): IncomeView? {
        val response = client.request("GET", "/api/v1/income", query = monthQuery(month))
        // A API responde 404 quando o mês ainda não tem renda configurada.
        if (response.status == 404) return null
        val body = JSONObject(response.orThrow("Não foi possível carregar a renda").body)
        return readIncome(body)
    }

    override suspend fun saveIncome(amount: Money): IncomeView = readIncome(
        client.requestJson(
            "PUT", "/api/v1/income",
            // O corpo aceita exatamente um campo; qualquer chave extra devolve 400.
            jsonBody = JSONObject().put("amount", amount.toApiAmount()).toString(),
            failure = "Não foi possível salvar a renda",
        ),
    )

    override suspend fun loadIncomeHistory(page: Int): IncomeHistoryPageView {
        val body = client.requestJson(
            "GET", "/api/v1/income/history", query = mapOf("page" to page.toString(), "size" to "20"),
            failure = "Não foi possível carregar o histórico de renda",
        )
        return IncomeHistoryPageView(
            items = body.getJSONArray("items").mapObjects {
                IncomeHistoryItemView(
                    id = it.getString("id"),
                    amount = Money.fromApiAmount(it.getString("amount")),
                    effectiveFrom = it.getString("effectiveFrom"),
                    changedAt = it.getString("changedAt"),
                )
            },
            page = body.optInt("page", page),
            hasNext = body.optBoolean("hasNext", false),
        )
    }

    // ------------------------------------------------------------- relatórios

    override suspend fun downloadReport(
        report: ReportId,
        from: String,
        to: String,
        format: ReportFormat,
    ): DownloadedReport = client.download(
        path = "/api/v1/reports/${report.api}",
        query = mapOf("from" to from, "to" to to, "format" to format.api),
        failure = "Não foi possível gerar o relatório",
    ) { suggestedName, stream ->
        // Diretório privado do app: compartilhar por FileProvider dispensa
        // permissão de armazenamento, que o minSdk 24 ainda exigiria.
        val directory = File(context.cacheDir, "relatorios").apply { mkdirs() }
        val fileName = suggestedName ?: "${report.api}_${from}_$to.${format.extension}"
        val target = File(directory, fileName)
        target.outputStream().use(stream::copyTo)
        DownloadedReport(fileName = fileName, location = target.absolutePath)
    }

    // ---------------------------------------------------------------- leitura

    private fun readEnvelope(json: JSONObject) = EnvelopeView(
        id = json.getString("id"),
        name = json.getString("name"),
        purpose = json.getString("purpose"),
        baseAmount = money(json.getJSONObject("baseAmount")),
        available = money(json.getJSONObject("available")),
        isNegative = json.optBoolean("isNegative", false),
        targetAmount = json.optJSONObject("targetAmount")?.let(::money),
        targetReachedAt = json.optStringOrNull("targetReachedAt"),
        annualExpense = json.optJSONObject("annualExpense")?.let {
            AnnualExpenseView(
                annualAmount = money(it.getJSONObject("annualAmount")),
                dueMonth = it.getInt("dueMonth"),
                dueDay = it.getInt("dueDay"),
                fundingMode = FundingMode.fromApiOrNull(it.optString("fundingMode")) ?: FundingMode.MONTHLY,
            )
        },
        goalProgress = json.optJSONObject("goalProgress")?.let {
            GoalProgressView(
                plannedAmount = money(it.getJSONObject("plannedAmount")),
                contributedAmount = money(it.getJSONObject("contributedAmount")),
                remainingAmount = money(it.getJSONObject("remainingAmount")),
                percent = it.getInt("percent"),
            )
        },
        role = role(json.optString("role")),
        archivedAt = json.optStringOrNull("archivedAt"),
    )

    private fun readEntry(json: JSONObject) = LedgerEntryView(
        id = json.getString("id"),
        envelopeId = json.getString("envelopeId"),
        kind = LedgerKind.fromApiOrNull(json.getString("kind")) ?: LedgerKind.EXPENSE,
        amount = money(json.getJSONObject("amount")),
        occurredAt = json.getString("occurredAt"),
        description = json.optStringOrNull("description"),
        targetJustReached = json.optBoolean("targetJustReached", false),
        deletedAt = json.optStringOrNull("deletedAt"),
    )

    private fun readIncome(json: JSONObject) = IncomeView(
        amount = Money.fromApiAmount(json.getString("amount")),
        effectiveFrom = json.getString("effectiveFrom"),
        changedAt = json.getString("changedAt"),
    )

    private fun money(json: JSONObject) = Money.fromApiAmount(json.getString("amount"))

    private fun role(api: String) =
        if (api.equals("PARTICIPANT", ignoreCase = true)) EnvelopeRole.PARTICIPANT else EnvelopeRole.OWNER

    private fun monthQuery(month: YearMonth?) = mapOf("month" to month?.toApiMonth())

    private fun credentials(email: String, password: String) =
        JSONObject().put("email", email).put("password", password).toString()
}

private fun moneyJson(amount: Money) = JSONObject().put("amount", amount.toApiAmount()).put("currency", "BRL")

private fun NewEnvelope.toJson(): String = JSONObject()
    .put("name", name)
    .put("purpose", purpose.api)
    .apply {
        if (purpose == EnvelopePurpose.ANNUAL_EXPENSE) {
            put("annualAmount", moneyJson(annualAmount ?: Money(0)))
            put("dueMonth", dueMonth)
            put("dueDay", dueDay)
            put("fundingMode", (fundingMode ?: FundingMode.MONTHLY).api)
        } else {
            put("baseAmount", moneyJson(baseAmount))
            targetAmount?.let { put("targetAmount", moneyJson(it)) }
        }
    }
    .toString()

private fun EnvelopeEdit.toJson(): String = JSONObject()
    .apply {
        name?.let { put("name", it) }
        baseAmount?.let { put("baseAmount", moneyJson(it)) }
        targetAmount?.let { put("targetAmount", moneyJson(it)) }
        annualAmount?.let { put("annualAmount", moneyJson(it)) }
        dueMonth?.let { put("dueMonth", it) }
        dueDay?.let { put("dueDay", it) }
        fundingMode?.let { put("fundingMode", it.api) }
    }
    .toString()

private fun NewLedgerEntry.toJson(): String = JSONObject()
    .put("kind", kind.api)
    .put("amount", moneyJson(amount))
    .put("occurredAt", occurredAt)
    .apply { description?.takeIf(String::isNotBlank)?.let { put("description", it) } }
    .toString()

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return List(array.length()) { array.getString(it) }
}

private fun <T> JSONArray.mapObjects(read: (JSONObject) -> T): List<T> =
    List(length()) { read(getJSONObject(it)) }
