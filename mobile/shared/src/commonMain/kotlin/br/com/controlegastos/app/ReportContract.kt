package br.com.controlegastos.app

enum class ReportFormat(val api: String, val label: String, val extension: String) {
    XLSX("xlsx", "Planilha (XLSX)", "xlsx"),
    CSV("csv", "Texto separado por vírgula (CSV)", "csv"),
}

enum class ReportId(val api: String, val label: String, val description: String) {
    EXPENSES_BY_PURPOSE(
        "expenses-by-purpose",
        "Gastos por tipo",
        "Total gasto em cada natureza de verba no período.",
    ),
    LIMIT_EXCEEDED_MONTHS(
        "limit-exceeded-months",
        "Limites extrapolados",
        "Meses em que uma verba de limite fechou negativa.",
    ),
    GOALS_BELOW_TARGET(
        "goals-below-target",
        "Metas abaixo do esperado",
        "Metas que não atingiram o aporte planejado no mês.",
    ),
}

/** Arquivo já gravado no dispositivo, pronto para abrir ou compartilhar. */
data class DownloadedReport(val fileName: String, val location: String)

/** Projeção de ritmo de uma verba LIMIT (strings decimais da API; sem recalcular no cliente). */
data class LimitSpendingPaceRow(
    val envelopeId: String,
    val name: String,
    val capacity: String,
    val spent: String,
    val projectedMonthSpend: String,
    val dailyRemainingAllowance: String,
    val projectedOverrunDay: String?,
    val month: String,
)

interface ReportGateway {
    suspend fun downloadReport(report: ReportId, from: String, to: String, format: ReportFormat): DownloadedReport

    suspend fun loadLimitSpendingPace(month: String): List<LimitSpendingPaceRow>
}
