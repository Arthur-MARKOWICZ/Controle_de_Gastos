package br.com.controlegastos.app

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Content(val dashboard: FinancialDashboard) : DashboardState
    data class Error(val message: String) : DashboardState
}

/**
 * Estado financeiro de um mês: saldos, verbas e as operações que os alteram.
 *
 * O mês corrente é enviado como `null` para o backend resolver o fuso de
 * referência; navegar para outro mês passa a enviá-lo explicitamente.
 * Toda escrita recarrega o mês, porque saldo e não alocado derivam do servidor.
 */
class FinanceDashboardController(
    private val ledger: LedgerGateway,
    private val currentMonth: YearMonth,
    private val envelopes: EnvelopeGateway = UnavailableGateway,
    private val income: IncomeGateway = UnavailableGateway,
) {
    var month: YearMonth = currentMonth
        private set

    var state: DashboardState = DashboardState.Loading
        private set

    /** Indica atualização em segundo plano sem esconder o conteúdo já carregado. */
    var refreshing: Boolean = false
        private set

    /** Última falha de carga, para a casca distinguir sessão expirada de erro comum. */
    var lastFailure: Throwable? = null
        private set

    val isCurrentMonth: Boolean get() = month == currentMonth

    val dashboard: FinancialDashboard? get() = (state as? DashboardState.Content)?.dashboard

    suspend fun refresh() {
        val keepContent = state is DashboardState.Content
        if (keepContent) refreshing = true else state = DashboardState.Loading
        try {
            lastFailure = null
            state = DashboardState.Content(ledger.loadDashboard(month.takeUnless { it == currentMonth }))
        } catch (failure: Throwable) {
            lastFailure = failure
            if (!keepContent) {
                state = DashboardState.Error(describeFailure(failure, "Não foi possível carregar suas verbas."))
            }
        } finally {
            refreshing = false
        }
    }

    suspend fun showPreviousMonth() {
        month = month.previous()
        state = DashboardState.Loading
        refresh()
    }

    suspend fun showNextMonth() {
        month = month.next()
        state = DashboardState.Loading
        refresh()
    }

    /** Verbas oferecidas no lançamento rápido de gasto; metas recebem aporte pelo próprio card. */
    val expenseEnvelopes: List<EnvelopeView>
        get() = envelopesOf(EnvelopePurpose.LIMIT, EnvelopePurpose.FIXED, EnvelopePurpose.ANNUAL_EXPENSE)

    /** @return confirmação do lançamento, com alerta quando o gasto deixou a verba negativa. */
    suspend fun registerEntry(envelopeId: String, entry: NewLedgerEntry): String {
        val name = envelopeNamed(envelopeId)
        val created = ledger.createEntry(envelopeId, entry)
        refresh()
        if (created.targetJustReached) return "Meta de $name alcançada."
        val confirmation = "${entry.kind.label} de ${entry.amount.toBrl()} em $name."
        val after = dashboard?.envelopes?.firstOrNull { it.id == envelopeId }
        return if (entry.kind == LedgerKind.EXPENSE && after?.isNegative == true) {
            "$confirmation A verba ficou negativa: ${after.available.toBrl()}."
        } else {
            confirmation
        }
    }

    private fun envelopeNamed(id: String): String =
        dashboard?.envelopes?.firstOrNull { it.id == id }?.name ?: "verba"

    suspend fun createEnvelope(envelope: NewEnvelope) {
        envelopes.createEnvelope(envelope)
        refresh()
    }

    suspend fun updateEnvelope(id: String, edit: EnvelopeEdit) {
        envelopes.updateEnvelope(id, edit)
        refresh()
    }

    suspend fun archiveEnvelope(id: String) {
        envelopes.archiveEnvelope(id)
        refresh()
    }

    suspend fun saveIncome(amount: Money) {
        income.saveIncome(amount)
        refresh()
    }

    fun envelopesOf(vararg purposes: EnvelopePurpose): List<EnvelopeView> {
        val wanted = purposes.map(EnvelopePurpose::api).toSet()
        return dashboard?.envelopes.orEmpty().filter { it.purpose in wanted }
    }
}
