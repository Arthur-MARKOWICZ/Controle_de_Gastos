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

    /** Última falha de carga, para a casca distinguir sessão expirada de erro comum. */
    var lastFailure: Throwable? = null
        private set

    val isCurrentMonth: Boolean get() = month == currentMonth

    val dashboard: FinancialDashboard? get() = (state as? DashboardState.Content)?.dashboard

    suspend fun refresh() {
        state = DashboardState.Loading
        state = try {
            lastFailure = null
            DashboardState.Content(ledger.loadDashboard(month.takeUnless { it == currentMonth }))
        } catch (failure: Throwable) {
            lastFailure = failure
            DashboardState.Error(describeFailure(failure, "Não foi possível carregar suas verbas."))
        }
    }

    suspend fun showPreviousMonth() {
        month = month.previous()
        refresh()
    }

    suspend fun showNextMonth() {
        month = month.next()
        refresh()
    }

    /** @return `true` quando o aporte fechou a meta, para a tela comemorar. */
    suspend fun registerEntry(envelopeId: String, entry: NewLedgerEntry): Boolean {
        val created = ledger.createEntry(envelopeId, entry)
        refresh()
        return created.targetJustReached
    }

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
