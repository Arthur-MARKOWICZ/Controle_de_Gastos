package br.com.controlegastos.app

/** Ações que um card de verba pode oferecer. */
internal enum class EnvelopeAction(val label: String) {
    REGISTER("Registrar"),
    CONTRIBUTE("Aportar"),
    ENTRIES("Lançamentos"),
    PARTICIPANTS("Participantes"),
    RECURRING("Recorrências"),
    EDIT("Editar"),
    ARCHIVE("Encerrar"),
}

/**
 * Ações do card: uma primária visível e as demais atrás de "Mais".
 *
 * Em 360 dp cinco botões lado a lado estouram a largura e deixam Encerrar
 * encostado em Registrar; separar a primária das outras evita o toque
 * acidental na ação difícil de desfazer.
 */
internal data class EnvelopeCardActions(
    val primary: EnvelopeAction,
    val menu: List<EnvelopeAction>,
) {
    val primaryLabel: String get() = primary.label

    /** Tipo de lançamento que a ação primária abre. */
    val primaryKind: LedgerKind
        get() = if (primary == EnvelopeAction.CONTRIBUTE) LedgerKind.CONTRIBUTION else LedgerKind.EXPENSE
}

/**
 * Participante só registra e consulta: abastecer, editar, encerrar e convidar
 * são do dono, e o backend recusa o resto com 403.
 */
internal fun envelopeCardActions(envelope: EnvelopeView): EnvelopeCardActions {
    val owner = envelope.isOwner
    val primary = if (owner && isGoal(envelope)) EnvelopeAction.CONTRIBUTE else EnvelopeAction.REGISTER
    val menu = if (owner) {
        listOf(
            EnvelopeAction.ENTRIES,
            EnvelopeAction.PARTICIPANTS,
            EnvelopeAction.RECURRING,
            EnvelopeAction.EDIT,
            EnvelopeAction.ARCHIVE,
        )
    } else {
        listOf(EnvelopeAction.ENTRIES)
    }
    return EnvelopeCardActions(primary, menu)
}

/** Verbas que recebem gasto; metas recebem aporte pelo próprio card. */
internal fun List<EnvelopeView>.expenseEnvelopes(): List<EnvelopeView> {
    val wanted = setOf(
        EnvelopePurpose.LIMIT.api,
        EnvelopePurpose.FIXED.api,
        EnvelopePurpose.ANNUAL_EXPENSE.api,
    )
    return filter { it.purpose in wanted }
}

internal fun isGoal(envelope: EnvelopeView): Boolean =
    envelope.purpose == EnvelopePurpose.GOAL.api || envelope.purpose == EnvelopePurpose.SAVINGS_TARGET.api

internal fun headlineAmount(envelope: EnvelopeView): String {
    val goal = envelope.goalProgress
    if (goal != null && envelope.purpose == EnvelopePurpose.GOAL.api) {
        return "Faltam ${goal.remainingAmount.toBrl()}"
    }
    return envelope.available.toBrl()
}

/**
 * Linha de estado do card, em texto.
 *
 * Emoji não é lido por todo leitor de tela nem sobrevive a toda fonte, então
 * o estado vem escrito; cor e ícone no card só reforçam o que já está dito.
 */
internal fun statusLine(envelope: EnvelopeView, pace: LimitSpendingPaceRow? = null): String {
    val goal = envelope.goalProgress
    val base = when {
        envelope.isNegative -> "Saldo negativo. O gasto foi registrado mesmo assim."
        envelope.targetReachedAt != null -> "Meta alcançada"
        goal != null && envelope.purpose == EnvelopePurpose.GOAL.api ->
            "Meta ${goal.plannedAmount.toBrl()} · ${goal.percent}% concluído"
        envelope.targetAmount != null -> "Alvo ${envelope.targetAmount.toBrl()}"
        envelope.annualExpense != null -> annualLine(envelope.annualExpense)
        else -> "Saldo disponível"
    }
    if (envelope.purpose != EnvelopePurpose.LIMIT.api || pace == null) return base
    return "$base · ${spendingPaceLabel(pace)}"
}

/** Texto de ritmo a partir da API — sem recalcular no cliente. */
internal fun spendingPaceLabel(pace: LimitSpendingPaceRow): String {
    val overrun = pace.projectedOverrunDay
    if (overrun != null) {
        val parts = overrun.split("-")
        if (parts.size == 3) {
            return "Estouro projetado em ${parts[2]}/${parts[1]}/${parts[0]}"
        }
        return "Estouro projetado em $overrun"
    }
    return "${Money.fromApiAmount(pace.dailyRemainingAllowance).toBrl()} por dia até o fim do mês"
}

private fun annualLine(annual: AnnualExpenseView): String {
    val day = annual.dueDay.toString().padStart(2, '0')
    val month = annual.dueMonth.toString().padStart(2, '0')
    return "${annual.annualAmount.toBrl()} por ano · vence em $day/$month · ${annual.fundingMode.label.lowercase()}"
}
