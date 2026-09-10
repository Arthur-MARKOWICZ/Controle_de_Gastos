package br.com.controlegastos.app

enum class LedgerKind(val api: String, val label: String) {
    EXPENSE("EXPENSE", "Gasto"),
    CONTRIBUTION("CONTRIBUTION", "Aporte"),
    ;

    companion object {
        fun fromApiOrNull(api: String): LedgerKind? = entries.firstOrNull { it.api == api }
    }
}

data class IncomeView(
    val amount: Money,
    val effectiveFrom: String,
    val changedAt: String,
)

data class FinancialDashboard(
    val income: IncomeView?,
    val allocated: Money,
    val unallocated: Money,
    val usagePct: Double,
    val envelopes: List<EnvelopeView>,
)

data class LedgerEntryView(
    val id: String,
    val envelopeId: String,
    val kind: LedgerKind,
    val amount: Money,
    val occurredAt: String,
    val description: String?,
    val targetJustReached: Boolean = false,
    val deletedAt: String? = null,
)

data class HistoryItemView(
    val entry: LedgerEntryView,
    val envelopeName: String,
    val purpose: String,
    val role: EnvelopeRole,
) {
    val isOwner: Boolean get() = role == EnvelopeRole.OWNER
}

data class HistoryPageView(
    val items: List<HistoryItemView>,
    val page: Int,
    val hasNext: Boolean,
)

data class PurposeTotalView(val purpose: String, val amount: Money)

data class MonthlyTotalView(val month: String, val amount: Money)

data class HistorySummaryView(
    val income: Money,
    val expenses: Money,
    val netBalance: Money,
    val accumulatedBalance: Money,
    val monthlyTotals: List<MonthlyTotalView>,
    val purposeTotals: List<PurposeTotalView>,
)

/** Lançamento novo. `occurredAt` é `YYYY-MM-DD` e não pode ser futuro. */
data class NewLedgerEntry(
    val kind: LedgerKind,
    val amount: Money,
    val occurredAt: String,
    val description: String? = null,
)

/**
 * Edição de lançamento. A data é imutável no backend: enviá-la devolve 400,
 * então ela não faz parte deste tipo.
 */
data class LedgerEntryEdit(
    val envelopeId: String,
    val amount: Money,
    val description: String?,
)

interface LedgerGateway {
    suspend fun loadDashboard(month: YearMonth?): FinancialDashboard
    suspend fun listEntries(envelopeId: String, month: YearMonth?): List<LedgerEntryView>
    suspend fun createEntry(envelopeId: String, entry: NewLedgerEntry): LedgerEntryView
    suspend fun loadHistory(from: String, to: String, page: Int, includeDeleted: Boolean): HistoryPageView
    suspend fun loadHistorySummary(from: String, to: String): HistorySummaryView
    suspend fun updateEntry(id: String, edit: LedgerEntryEdit): LedgerEntryView
    suspend fun deleteEntry(id: String)
}

interface IncomeGateway {
    suspend fun loadIncome(month: YearMonth?): IncomeView?
    suspend fun saveIncome(amount: Money): IncomeView
    suspend fun loadIncomeHistory(page: Int): IncomeHistoryPageView
}

data class IncomeHistoryItemView(
    val id: String,
    val amount: Money,
    val effectiveFrom: String,
    val changedAt: String,
)

data class IncomeHistoryPageView(
    val items: List<IncomeHistoryItemView>,
    val page: Int,
    val hasNext: Boolean,
)
