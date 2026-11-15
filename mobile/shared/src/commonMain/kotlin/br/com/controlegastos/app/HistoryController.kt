package br.com.controlegastos.app

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Content(val summary: HistorySummaryView, val page: HistoryPageView) : HistoryState
    data class Error(val message: String) : HistoryState
}

/**
 * Histórico de lançamentos de um período.
 *
 * O período padrão é o mês corrente inteiro; `from` e `to` são obrigatórios na
 * API. Editar e excluir só aparecem para quem é dono da verba — o backend
 * recusa o resto com 403, e a interface não oferece o que vai falhar.
 */
class HistoryController(
    private val gateway: LedgerGateway,
    currentMonth: YearMonth,
) {
    var month: YearMonth = currentMonth
        private set

    var page: Int = 0
        private set

    var includeDeleted: Boolean = false
        private set

    /** Termo de busca na descrição, já sem espaços nas pontas. */
    var query: String = ""
        private set

    /** Verba escolhida no filtro; nula mostra todas as visíveis. */
    var envelopeId: String? = null
        private set

    val filtersActive: Boolean get() = filter().isActive

    var state: HistoryState = HistoryState.Loading
        private set

    /** Indica atualização em segundo plano sem esconder o conteúdo já carregado. */
    var refreshing: Boolean = false
        private set

    /** Última falha de carga, para a casca distinguir sessão expirada de erro comum. */
    var lastFailure: Throwable? = null
        private set

    suspend fun refresh() {
        val keepContent = state is HistoryState.Content
        if (keepContent) refreshing = true else state = HistoryState.Loading
        try {
            lastFailure = null
            state = HistoryState.Content(
                summary = gateway.loadHistorySummary(month.firstDay(), month.lastDay()),
                page = gateway.loadHistory(month.firstDay(), month.lastDay(), page, includeDeleted, filter()),
            )
        } catch (failure: Throwable) {
            lastFailure = failure
            if (!keepContent) {
                state = HistoryState.Error(describeFailure(failure, "Não foi possível carregar o histórico."))
            }
        } finally {
            refreshing = false
        }
    }

    suspend fun showPreviousMonth() {
        month = month.previous()
        page = 0
        state = HistoryState.Loading
        refresh()
    }

    suspend fun showNextMonth() {
        month = month.next()
        page = 0
        state = HistoryState.Loading
        refresh()
    }

    suspend fun showDeleted(include: Boolean) {
        includeDeleted = include
        page = 0
        state = HistoryState.Loading
        refresh()
    }

    /** Busca na descrição; mantém o mês e volta para a primeira página. */
    suspend fun search(text: String) {
        query = text.trim()
        restartPaging()
    }

    suspend fun filterEnvelope(id: String?) {
        envelopeId = id
        restartPaging()
    }

    suspend fun clearFilters() {
        query = ""
        envelopeId = null
        restartPaging()
    }

    private suspend fun restartPaging() {
        page = 0
        state = HistoryState.Loading
        refresh()
    }

    private fun filter() = HistoryFilter(query = query, envelopeId = envelopeId)

    suspend fun nextPage() {
        val content = state as? HistoryState.Content ?: return
        if (!content.page.hasNext) return
        page++
        refresh()
    }

    suspend fun previousPage() {
        if (page == 0) return
        page--
        refresh()
    }

    suspend fun editEntry(id: String, edit: LedgerEntryEdit) {
        gateway.updateEntry(id, edit)
        refresh()
    }

    suspend fun deleteEntry(id: String) {
        gateway.deleteEntry(id)
        refresh()
    }

    suspend fun listRevisions(entryId: String): List<LedgerEntryRevisionView> =
        gateway.listEntryRevisions(entryId).items
}

/** Estado vazio da lista: a mensagem e se oferece limpar os filtros. */
internal data class HistoryEmptyState(val message: String, val offerClearFilters: Boolean)

/**
 * O que a lista do histórico mostra além dos itens, decidido fora da tela para
 * ser testável: o total filtrado (só com filtro ativo) e o estado vazio.
 */
internal data class HistoryListContent(val resultsTotal: String?, val emptyState: HistoryEmptyState?)

internal fun historyListContent(page: HistoryPageView, filtersActive: Boolean): HistoryListContent {
    val total = if (filtersActive) {
        "Total dos resultados: ${page.matchedTotal.toBrl()} (todas as páginas, sem excluídos)"
    } else {
        null
    }
    val empty = when {
        page.items.isNotEmpty() -> null
        filtersActive -> HistoryEmptyState("Nenhum gasto corresponde aos filtros.", offerClearFilters = true)
        else -> HistoryEmptyState("Nenhum lançamento neste período.", offerClearFilters = false)
    }
    return HistoryListContent(total, empty)
}
