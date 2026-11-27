package br.com.controlegastos.app

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Content(val summary: HistorySummaryView, val page: HistoryPageView) : HistoryState
    data class Error(val message: String) : HistoryState
}

/** Presets de período alinhados ao Histórico web (DESIGN 5A). */
enum class HistoryPeriodPreset {
    THIS_MONTH,
    PREV_MONTH,
    LAST_7,
    CUSTOM,
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
    private val referenceMonth: YearMonth,
) {
    var month: YearMonth = referenceMonth
        private set

    var from: String = referenceMonth.firstDay()
        private set

    var to: String = referenceMonth.lastDay()
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

    fun periodPreset(today: String): HistoryPeriodPreset {
        val thisMonthFrom = referenceMonth.firstDay()
        val previous = referenceMonth.previous()
        val lastSevenFrom = shiftIsoDays(today, -6)
        return when {
            from == thisMonthFrom && (to == today || to == referenceMonth.lastDay()) -> HistoryPeriodPreset.THIS_MONTH
            from == previous.firstDay() && to == previous.lastDay() -> HistoryPeriodPreset.PREV_MONTH
            from == lastSevenFrom && to == today -> HistoryPeriodPreset.LAST_7
            else -> HistoryPeriodPreset.CUSTOM
        }
    }

    suspend fun refresh() {
        val keepContent = state is HistoryState.Content
        if (keepContent) refreshing = true else state = HistoryState.Loading
        try {
            lastFailure = null
            state = HistoryState.Content(
                summary = gateway.loadHistorySummary(from, to),
                page = gateway.loadHistory(from, to, page, includeDeleted, filter()),
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

    suspend fun showThisMonth(today: String) {
        month = referenceMonth
        from = referenceMonth.firstDay()
        to = today
        page = 0
        state = HistoryState.Loading
        refresh()
    }

    suspend fun showPreviousCalendarMonth() {
        val previous = referenceMonth.previous()
        month = previous
        from = previous.firstDay()
        to = previous.lastDay()
        page = 0
        state = HistoryState.Loading
        refresh()
    }

    suspend fun showLast7Days(today: String) {
        from = shiftIsoDays(today, -6)
        to = today
        month = YearMonth.parse(today.substring(0, 7))
        page = 0
        state = HistoryState.Loading
        refresh()
    }

    suspend fun showPreviousMonth() {
        month = month.previous()
        from = month.firstDay()
        to = month.lastDay()
        page = 0
        state = HistoryState.Loading
        refresh()
    }

    suspend fun showNextMonth() {
        month = month.next()
        from = month.firstDay()
        to = month.lastDay()
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

/** Agrupa lançamentos por dia, mais recentes primeiro. */
internal fun groupHistoryByDay(items: List<HistoryItemView>): List<Pair<String, List<HistoryItemView>>> =
    items
        .groupBy { it.entry.occurredAt }
        .toList()
        .sortedByDescending { it.first }

/** Cabeçalho de dia: Hoje / Ontem / data pt-BR. */
internal fun formatHistoryDayHeading(isoDate: String, today: String): String = when (isoDate) {
    today -> "Hoje · ${formatIsoDateBr(isoDate)}"
    previousIsoDay(today) -> "Ontem · ${formatIsoDateBr(isoDate)}"
    else -> formatIsoDateBr(isoDate)
}
