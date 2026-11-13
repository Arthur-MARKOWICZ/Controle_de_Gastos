package br.com.controlegastos.app

/** Recorrência mensal de uma verba; só o dono a vê (ADR-0031). */
data class RecurringEntryView(
    val id: String,
    val envelopeId: String,
    val kind: LedgerKind,
    val amount: Money,
    val dayOfMonth: Int,
    val description: String?,
    val active: Boolean,
    val firstCompetence: String,
)

data class RecurringEntryInput(
    val kind: LedgerKind,
    val amount: Money,
    val dayOfMonth: Int,
    val description: String?,
)

/** Pendência de confirmação; nada entra no saldo antes de confirmar. */
data class PendingOccurrenceView(
    val id: String,
    val envelopeId: String,
    val envelopeName: String,
    val kind: LedgerKind,
    val description: String?,
    val suggestedAmount: Money,
    val dueDate: String,
    val competence: String,
) {
    val label: String get() = description ?: envelopeName
}

data class PendingOccurrencesView(
    val current: List<PendingOccurrenceView> = emptyList(),
    val overdue: List<PendingOccurrenceView> = emptyList(),
)

interface RecurringGateway {
    suspend fun listRecurring(envelopeId: String): List<RecurringEntryView>
    suspend fun createRecurring(envelopeId: String, input: RecurringEntryInput): RecurringEntryView
    suspend fun updateRecurring(envelopeId: String, id: String, input: RecurringEntryInput, active: Boolean): RecurringEntryView
    suspend fun deleteRecurring(envelopeId: String, id: String)
    suspend fun listPendingOccurrences(): PendingOccurrencesView

    /** `amount`/`occurredAt` nulos usam o valor sugerido e a data prevista. */
    suspend fun confirmOccurrence(id: String, amount: Money? = null, occurredAt: String? = null)
    suspend fun discardOccurrence(id: String)
}

internal fun recurringSummary(entry: RecurringEntryView): String =
    "${entry.amount.toBrl()} · todo dia ${entry.dayOfMonth} · ${if (entry.active) "Ativa" else "Inativa"}"

internal fun shortMonthHint(day: Int?): String? =
    if (day != null && day in 29..31) "Em meses mais curtos, a data será o último dia do mês." else null

/** @return mensagem do primeiro problema, ou `null` quando o formulário pode ser enviado. */
internal fun validateRecurringInput(amount: Money, dayText: String): String? {
    if (amount.cents <= 0) return "Informe um valor positivo"
    val day = dayText.trim().toIntOrNull()
    if (day == null || day !in 1..31) return "Escolha um dia entre 1 e 31"
    return null
}

/** Metas recebem aporte; as demais verbas, gasto. */
internal fun recurringKindFor(purpose: String): LedgerKind =
    if (purpose == EnvelopePurpose.GOAL.api || purpose == EnvelopePurpose.SAVINGS_TARGET.api) {
        LedgerKind.CONTRIBUTION
    } else {
        LedgerKind.EXPENSE
    }

/**
 * Pendências do titular para a tela inicial.
 *
 * Uma falha mantém o item, explica o motivo e recarrega a lista, porque outro
 * dispositivo pode ter confirmado ou descartado a mesma pendência.
 */
class PendingOccurrencesController(private val gateway: RecurringGateway) {
    var pending: PendingOccurrencesView = PendingOccurrencesView()
        private set

    var error: String? = null
        private set

    val hasPending: Boolean get() = pending.current.isNotEmpty() || pending.overdue.isNotEmpty()

    suspend fun refresh() {
        runCatching { gateway.listPendingOccurrences() }
            .onSuccess { pending = it }
            .onFailure { error = describeFailure(it, "Não foi possível carregar as recorrências pendentes.") }
    }

    suspend fun confirm(id: String, amount: Money? = null, occurredAt: String? = null): Boolean =
        resolve(id, "Não foi possível confirmar a pendência.") { gateway.confirmOccurrence(id, amount, occurredAt) }

    suspend fun discard(id: String): Boolean =
        resolve(id, "Não foi possível descartar a pendência.") { gateway.discardOccurrence(id) }

    private suspend fun resolve(id: String, fallback: String, action: suspend () -> Unit): Boolean {
        error = null
        return runCatching { action() }
            .onSuccess {
                pending = PendingOccurrencesView(
                    current = pending.current.filterNot { it.id == id },
                    overdue = pending.overdue.filterNot { it.id == id },
                )
            }
            .onFailure {
                error = describeFailure(it, fallback)
                refresh()
            }
            .isSuccess
    }
}
