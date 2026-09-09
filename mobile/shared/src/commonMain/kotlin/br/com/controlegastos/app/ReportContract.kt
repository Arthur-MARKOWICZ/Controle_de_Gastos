package br.com.controlegastos.app

enum class ReportFormat(val api: String, val label: String, val extension: String) {
    XLSX("xlsx", "Planilha (XLSX)", "xlsx"),
    CSV("csv", "Texto separado por vírgula (CSV)", "csv"),
}

enum class ReportId(val api: String, val label: String, val description: String, val wholeMonths: Boolean) {
    EXPENSES_BY_PURPOSE(
        "expenses-by-purpose",
        "Gastos por tipo",
        "Total gasto em cada natureza de verba no período.",
        wholeMonths = false,
    ),
    LIMIT_EXCEEDED_MONTHS(
        "limit-exceeded-months",
        "Limites extrapolados",
        "Meses em que uma verba de limite fechou negativa.",
        wholeMonths = true,
    ),
    GOALS_BELOW_TARGET(
        "goals-below-target",
        "Metas abaixo do esperado",
        "Metas que não atingiram o aporte planejado no mês.",
        wholeMonths = true,
    ),
}

/** Arquivo já gravado no dispositivo, pronto para abrir ou compartilhar. */
data class DownloadedReport(val fileName: String, val location: String)

interface ReportGateway {
    suspend fun downloadReport(report: ReportId, from: String, to: String, format: ReportFormat): DownloadedReport
}
