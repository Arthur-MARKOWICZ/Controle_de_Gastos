package br.com.controlegastos.app

/**
 * Conjunto de portas que a plataforma injeta no aplicativo.
 *
 * Agrupá-las evita uma lista longa de parâmetros em [VerbasApp] e deixa
 * explícito que uma plataforma sem rede fornece o conjunto indisponível.
 */
data class VerbasGateways(
    val auth: AuthGateway,
    val envelopes: EnvelopeGateway,
    val ledger: LedgerGateway,
    val income: IncomeGateway,
    val reports: ReportGateway,
) {
    companion object {
        val Unavailable = VerbasGateways(
            auth = UnavailableAuthGateway,
            envelopes = UnavailableGateway,
            ledger = UnavailableGateway,
            income = UnavailableGateway,
            reports = UnavailableGateway,
        )
    }
}

private const val UNAVAILABLE = "Dados financeiros não configurados para esta plataforma"

/** Usado pelo iOS, que ainda não tem cliente HTTP (ADR-004). */
object UnavailableGateway : EnvelopeGateway, LedgerGateway, IncomeGateway, ReportGateway {
    override suspend fun listEnvelopes(month: YearMonth?): List<EnvelopeView> = error(UNAVAILABLE)
    override suspend fun createEnvelope(envelope: NewEnvelope): EnvelopeView = error(UNAVAILABLE)
    override suspend fun updateEnvelope(id: String, edit: EnvelopeEdit): EnvelopeView = error(UNAVAILABLE)
    override suspend fun archiveEnvelope(id: String) = error(UNAVAILABLE)

    override suspend fun loadDashboard(month: YearMonth?): FinancialDashboard = error(UNAVAILABLE)
    override suspend fun listEntries(envelopeId: String, month: YearMonth?): List<LedgerEntryView> = error(UNAVAILABLE)
    override suspend fun createEntry(envelopeId: String, entry: NewLedgerEntry): LedgerEntryView = error(UNAVAILABLE)
    override suspend fun loadHistory(from: String, to: String, page: Int, includeDeleted: Boolean): HistoryPageView =
        error(UNAVAILABLE)
    override suspend fun loadHistorySummary(from: String, to: String): HistorySummaryView = error(UNAVAILABLE)
    override suspend fun updateEntry(id: String, edit: LedgerEntryEdit): LedgerEntryView = error(UNAVAILABLE)
    override suspend fun deleteEntry(id: String) = error(UNAVAILABLE)

    override suspend fun loadIncome(month: YearMonth?): IncomeView? = error(UNAVAILABLE)
    override suspend fun saveIncome(amount: Money): IncomeView = error(UNAVAILABLE)
    override suspend fun loadIncomeHistory(page: Int): IncomeHistoryPageView = error(UNAVAILABLE)

    override suspend fun downloadReport(
        report: ReportId,
        from: String,
        to: String,
        format: ReportFormat,
    ): DownloadedReport = error(UNAVAILABLE)
}
