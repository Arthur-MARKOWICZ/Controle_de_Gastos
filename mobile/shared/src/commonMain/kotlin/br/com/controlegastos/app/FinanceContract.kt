package br.com.controlegastos.app

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Content(val dashboard: FinancialDashboard) : DashboardState
    data class Error(val message: String) : DashboardState
}

/** Confirmação de lançamento com id para o snackbar Desfazer. */
data class RegisterEntryResult(
    val message: String,
    val entryId: String,
)

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
    private val alerts: AlertGateway = UnavailableGateway,
    private val reports: ReportGateway = UnavailableGateway,
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

    /** Alertas persistentes de saldo negativo ainda ativos para o titular. */
    var activeAlerts: List<InAppAlertView> = emptyList()
        private set

    /** Ritmo LIMIT por envelopeId; vazio se a API falhar. */
    var spendingPaceById: Map<String, LimitSpendingPaceRow> = emptyMap()
        private set

    val isCurrentMonth: Boolean get() = month == currentMonth

    val dashboard: FinancialDashboard? get() = (state as? DashboardState.Content)?.dashboard

    suspend fun refresh() {
        val keepContent = state is DashboardState.Content
        if (keepContent) refreshing = true else state = DashboardState.Loading
        try {
            lastFailure = null
            state = DashboardState.Content(ledger.loadDashboard(month.takeUnless { it == currentMonth }))
            activeAlerts = runCatching { alerts.listActiveAlerts() }.getOrDefault(emptyList())
            spendingPaceById = runCatching {
                reports.loadLimitSpendingPace(month.toApiMonth()).associateBy { it.envelopeId }
            }.getOrDefault(emptyMap())
        } catch (failure: Throwable) {
            lastFailure = failure
            if (!keepContent) {
                state = DashboardState.Error(describeFailure(failure, "Não foi possível carregar suas verbas."))
            }
        } finally {
            refreshing = false
        }
    }

    suspend fun dismissAlert(id: String) {
        alerts.dismissAlert(id)
        activeAlerts = activeAlerts.filterNot { it.id == id }
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
        get() = dashboard?.envelopes.orEmpty().expenseEnvelopes()

    suspend fun loadExpenseSuggestions(): ExpenseSuggestions = ledger.loadExpenseSuggestions()

    /**
     * @param envelopeName nome conhecido por quem chamou, para o lançamento feito
     * de uma tela cujo mês não é o do painel ainda ser confirmado pelo nome certo.
     * @return confirmação do lançamento (mensagem + id), com alerta quando o gasto
     * deixou a verba negativa.
     */
    suspend fun registerEntry(
        envelopeId: String,
        entry: NewLedgerEntry,
        envelopeName: String? = null,
    ): RegisterEntryResult {
        val name = envelopeName ?: envelopeNamed(envelopeId)
        val created = ledger.createEntry(envelopeId, entry)
        refresh()
        if (created.targetJustReached) {
            return RegisterEntryResult("Meta de $name alcançada.", created.id)
        }
        val confirmation = "${entry.kind.label} de ${entry.amount.toBrl()} em $name."
        val after = dashboard?.envelopes?.firstOrNull { it.id == envelopeId }
        val message = if (entry.kind == LedgerKind.EXPENSE && after?.isNegative == true) {
            "$confirmation A verba ficou negativa: ${after.available.toBrl()}."
        } else {
            confirmation
        }
        return RegisterEntryResult(message, created.id)
    }

    /** Últimos lançamentos do mês para a Visão geral; não inclui histórico de renda. */
    suspend fun loadRecentEntries(limit: Int = 5): List<HistoryItemView> {
        val page = ledger.loadHistory(
            from = month.firstDay(),
            to = month.lastDay(),
            page = 0,
            includeDeleted = false,
            size = limit,
        )
        return page.items.take(limit)
    }

    /** Soft-delete para Desfazer imediatamente após registrar. */
    suspend fun deleteEntry(id: String) {
        ledger.deleteEntry(id)
        refresh()
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
