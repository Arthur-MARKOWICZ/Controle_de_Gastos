package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val LIMIT_PURPOSES = arrayOf(EnvelopePurpose.LIMIT, EnvelopePurpose.FIXED)
private val GOAL_PURPOSES = arrayOf(EnvelopePurpose.GOAL, EnvelopePurpose.SAVINGS_TARGET)

/** Diálogo aberto sobre uma tela de mês. */
private sealed interface MonthDialog {
    /** Folha de lançamento: uma verba fixa pelo card, ou a escolha entre as do mês. */
    data class Entry(
        val envelopes: List<EnvelopeView>,
        val initialEnvelopeId: String?,
        val kind: LedgerKind,
    ) : MonthDialog
    data class Edit(val envelope: EnvelopeView) : MonthDialog
    data class Archive(val envelope: EnvelopeView) : MonthDialog
    data class Participants(val envelope: EnvelopeView) : MonthDialog
    data class Recurring(val envelope: EnvelopeView) : MonthDialog
    data class AdjustOccurrence(val item: PendingOccurrenceView) : MonthDialog
    data class DiscardOccurrence(val item: PendingOccurrenceView) : MonthDialog
    data class Create(val purpose: EnvelopePurpose, val allowed: List<EnvelopePurpose>) : MonthDialog
    data object Income : MonthDialog
    data object Glossary : MonthDialog
}

/** Lançamento aberto pelo card: a verba já está escolhida e o tipo vem da ação primária. */
private fun entrySheetFor(envelope: EnvelopeView): MonthDialog.Entry = MonthDialog.Entry(
    envelopes = listOf(envelope),
    initialEnvelopeId = envelope.id,
    kind = envelopeCardActions(envelope).primaryKind,
)

/**
 * Telas que operam sobre o mês corrente do [controller].
 *
 * As quatro compartilham diálogos, mês e recarga, então vivem num único host:
 * duplicar esse estado por tela faria cada uma divergir da outra.
 */
@Composable
internal fun MonthWorkspaceScreen(
    route: Route,
    controller: FinanceDashboardController,
    sharing: SharingGateway,
    today: String,
    recurring: RecurringGateway = UnavailableGateway,
    onOpenEntries: (EnvelopeView) -> Unit,
    onNotify: (String) -> Unit,
    onSessionExpired: () -> Unit = {},
    onMonthChanged: (YearMonth) -> Unit = {},
    quickExpenseRequested: Boolean = false,
    onQuickExpenseHandled: () -> Unit = {},
) {
    var state by remember(controller) { mutableStateOf(controller.state) }
    var alerts by remember(controller) { mutableStateOf(controller.activeAlerts) }
    var dialog by remember { mutableStateOf<MonthDialog?>(null) }
    val scope = rememberCoroutineScope()
    val pendingController = remember(recurring) { PendingOccurrencesController(recurring) }
    var pending by remember(recurring) { mutableStateOf(pendingController.pending) }
    var pendingError by remember(recurring) { mutableStateOf<String?>(null) }
    var pendingBusyId by remember(recurring) { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(quickExpenseRequested) {
        if (quickExpenseRequested) {
            dialog = MonthDialog.Entry(
                envelopes = controller.expenseEnvelopes,
                initialEnvelopeId = null,
                kind = LedgerKind.EXPENSE,
            )
            onQuickExpenseHandled()
        }
    }

    fun reload() = scope.launch {
        controller.refresh()
        state = controller.state
        alerts = controller.activeAlerts
        if (recurring !== UnavailableGateway) {
            pendingController.refresh()
            pending = pendingController.pending
        }
        if (controller.lastFailure?.isSessionExpired() == true) onSessionExpired()
    }

    // O controlador é compartilhado pelas quatro abas de mês; recarregar a cada
    // troca faria quatro chamadas para os mesmos dados e piscaria a tela.
    androidx.compose.runtime.LaunchedEffect(controller) {
        if (controller.dashboard == null) reload()
    }

    /** Dispensa o alerta persistente; a lista é a mesma em Visão geral e em Verbas. */
    fun dismissAlert(id: String) {
        scope.launch {
            runCatching { controller.dismissAlert(id) }
                .onSuccess { alerts = controller.activeAlerts }
                .onFailure { onNotify(describeFailure(it, "Não foi possível dispensar o alerta.")) }
        }
    }

    /** Confirma ou descarta a pendência; o saldo vem do servidor, então o mês é recarregado. */
    fun resolvePending(item: PendingOccurrenceView, action: suspend () -> Boolean, success: String) {
        if (pendingBusyId != null) return
        pendingBusyId = item.id
        scope.launch {
            val ok = action()
            pending = pendingController.pending
            pendingError = pendingController.error
            pendingBusyId = null
            if (ok) {
                onNotify(success)
                controller.refresh()
                state = controller.state
                alerts = controller.activeAlerts
            }
        }
    }

    /** Executa a escrita, devolve a mensagem de erro à tela e sincroniza o estado. */
    fun submit(result: FormResult, action: suspend () -> String?) {
        scope.launch {
            val outcome = runCatching { action() }
            state = controller.state
            alerts = controller.activeAlerts
            if (outcome.exceptionOrNull()?.isSessionExpired() == true) onSessionExpired()
            outcome
                .onSuccess { notice ->
                    result(null)
                    notice?.let(onNotify)
                }
                .onFailure { result(describeFailure(it, "Não foi possível concluir a operação.")) }
        }
    }

    MonthScreen(
        controller = controller,
        state = state,
        onRefresh = { reload() },
        onPreviousMonth = {
            scope.launch {
                controller.showPreviousMonth()
                state = controller.state
                onMonthChanged(controller.month)
            }
        },
        onNextMonth = {
            scope.launch {
                controller.showNextMonth()
                state = controller.state
                onMonthChanged(controller.month)
            }
        },
    ) { dashboard ->
        when (route) {
            Route.Envelopes -> {
                item {
                    SectionHeader(
                        "LIMITES E COMPROMISSOS",
                        "Suas verbas",
                        "Limites e compromissos reiniciam no valor-base a cada mês.",
                    )
                }
                item { NegativeBalanceAlert(dashboard.envelopes) }
                item { PersistentNegativeAlerts(alerts, ::dismissAlert) }
                envelopeSection(
                    title = "Limites de gasto",
                    envelopes = dashboard.envelopes.filter { it.purpose == EnvelopePurpose.LIMIT.api },
                    emptyMessage = "Nenhum limite de gasto neste mês.",
                    onRegister = { dialog = entrySheetFor(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
                    onParticipants = { dialog = MonthDialog.Participants(it) },
                    onRecurring = { dialog = MonthDialog.Recurring(it) },
                )
                envelopeSection(
                    title = "Compromissos fixos",
                    envelopes = dashboard.envelopes.filter { it.purpose == EnvelopePurpose.FIXED.api },
                    emptyMessage = "Nenhum compromisso fixo neste mês.",
                    onRegister = { dialog = entrySheetFor(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
                    onParticipants = { dialog = MonthDialog.Participants(it) },
                    onRecurring = { dialog = MonthDialog.Recurring(it) },
                )
                item {
                    NewEnvelopeButton("Nova verba") {
                        dialog = MonthDialog.Create(EnvelopePurpose.LIMIT, LIMIT_PURPOSES.toList())
                    }
                }
            }

            Route.Goals -> {
                item { SectionHeader("OBJETIVOS", "Metas", "Acompanhe o quanto já foi guardado.") }
                envelopeSection(
                    title = "Metas de aporte",
                    envelopes = dashboard.envelopes.filter { it.purpose == EnvelopePurpose.GOAL.api },
                    emptyMessage = "Nenhuma meta de aporte neste mês.",
                    onRegister = { dialog = entrySheetFor(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
                    onParticipants = { dialog = MonthDialog.Participants(it) },
                    onRecurring = { dialog = MonthDialog.Recurring(it) },
                )
                envelopeSection(
                    title = "Metas de acumulação",
                    envelopes = dashboard.envelopes.filter { it.purpose == EnvelopePurpose.SAVINGS_TARGET.api },
                    emptyMessage = "Nenhuma meta de acumulação neste mês.",
                    onRegister = { dialog = entrySheetFor(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
                    onParticipants = { dialog = MonthDialog.Participants(it) },
                    onRecurring = { dialog = MonthDialog.Recurring(it) },
                )
                item {
                    NewEnvelopeButton("Nova meta") {
                        dialog = MonthDialog.Create(EnvelopePurpose.GOAL, GOAL_PURPOSES.toList())
                    }
                }
            }

            Route.AnnualExpenses -> {
                item {
                    SectionHeader(
                        "PROVISÃO",
                        "Gastos anuais",
                        "Despesas que vencem uma vez por ano e são reservadas até lá.",
                    )
                }
                envelopeSection(
                    title = "Provisões do ano",
                    envelopes = dashboard.envelopes.filter { it.purpose == EnvelopePurpose.ANNUAL_EXPENSE.api },
                    emptyMessage = "Nenhum gasto anual cadastrado.",
                    onRegister = { dialog = entrySheetFor(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
                    onParticipants = { dialog = MonthDialog.Participants(it) },
                    onRecurring = { dialog = MonthDialog.Recurring(it) },
                )
                item {
                    NewEnvelopeButton("Novo gasto anual") {
                        dialog = MonthDialog.Create(
                            EnvelopePurpose.ANNUAL_EXPENSE,
                            listOf(EnvelopePurpose.ANNUAL_EXPENSE),
                        )
                    }
                }
            }

            else -> {
                item {
                    SectionHeader(
                        "VISÃO ATUAL",
                        "Seu mês em números",
                        "Confira o que já está reservado antes do próximo gasto.",
                    )
                }
                item { IncomeSummaryCard(dashboard) { dialog = MonthDialog.Income } }
                item { NegativeBalanceAlert(dashboard.envelopes) }
                // O estouro precisa aparecer onde a pessoa começa o dia, não só
                // na aba Verbas; dispensar aqui dispensa nas duas telas.
                item { PersistentNegativeAlerts(alerts, ::dismissAlert) }
                // Confirmar o aluguel deve custar um toque, logo na abertura do app.
                item {
                    PendingOccurrencesSection(
                        pending = pending,
                        error = pendingError,
                        busyId = pendingBusyId,
                        onConfirm = { item ->
                            resolvePending(item, { pendingController.confirm(item.id) }, "${item.label} confirmado.")
                        },
                        onAdjust = { dialog = MonthDialog.AdjustOccurrence(it) },
                        onDiscard = { dialog = MonthDialog.DiscardOccurrence(it) },
                    )
                }
                envelopeSection(
                    title = "Suas verbas",
                    envelopes = dashboard.envelopes,
                    emptyMessage = "Nenhuma verba neste mês. Crie a primeira para começar a reservar.",
                    onRegister = { dialog = entrySheetFor(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
                    onParticipants = { dialog = MonthDialog.Participants(it) },
                    onRecurring = { dialog = MonthDialog.Recurring(it) },
                )
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NewEnvelopeButton("Nova verba") {
                            dialog = MonthDialog.Create(EnvelopePurpose.LIMIT, EnvelopePurpose.entries)
                        }
                        OutlinedButton(
                            onClick = { dialog = MonthDialog.Glossary },
                            modifier = Modifier.fillMaxWidth().height(TouchTarget),
                        ) { Text("O que significa cada natureza") }
                    }
                }
            }
        }
    }

    when (val open = dialog) {
        null -> Unit
        is MonthDialog.Entry -> EntrySheet(
            envelopes = open.envelopes,
            initialEnvelopeId = open.initialEnvelopeId,
            kind = open.kind,
            month = controller.month,
            today = today,
            onSubmit = { envelopeId, entry, result ->
                submit(result) { controller.registerEntry(envelopeId, entry) }
            },
            onDismiss = { dialog = null },
        )
        is MonthDialog.Edit -> EnvelopeEditDialog(
            envelope = open.envelope,
            onSubmit = { edit, result ->
                submit(result) {
                    if (edit.isEmpty) null else { controller.updateEnvelope(open.envelope.id, edit); null }
                }
            },
            onDismiss = { dialog = null },
        )
        is MonthDialog.Archive -> ConfirmDialog(
            title = "Encerrar ${open.envelope.name}?",
            message = "A verba deixa de aparecer nos próximos meses. Os lançamentos já registrados continuam no " +
                "histórico.",
            confirmLabel = "Encerrar",
            destructive = true,
            onConfirm = {
                dialog = null
                submit({ it?.let(onNotify) }) { controller.archiveEnvelope(open.envelope.id); null }
            },
            onDismiss = { dialog = null },
        )
        is MonthDialog.Participants -> ParticipantsDialog(
            envelope = open.envelope,
            sharing = sharing,
            onNotify = onNotify,
            onDismiss = { dialog = null },
        )
        is MonthDialog.Recurring -> RecurringEntriesDialog(
            envelope = open.envelope,
            gateway = recurring,
            onNotify = onNotify,
            onDismiss = {
                dialog = null
                reload()
            },
        )
        is MonthDialog.AdjustOccurrence -> AdjustOccurrenceDialog(
            item = open.item,
            today = today,
            onSubmit = { amount, date ->
                dialog = null
                resolvePending(open.item, { pendingController.confirm(open.item.id, amount, date) },
                    "${open.item.label} confirmado com ${amount.toBrl()}.")
            },
            onDismiss = { dialog = null },
        )
        is MonthDialog.DiscardOccurrence -> ConfirmDialog(
            title = "Descartar ${open.item.label}?",
            message = "Nenhum lançamento será criado para este mês. A recorrência continua nos próximos meses.",
            confirmLabel = "Descartar",
            destructive = true,
            onConfirm = {
                dialog = null
                resolvePending(open.item, { pendingController.discard(open.item.id) }, "${open.item.label} descartado.")
            },
            onDismiss = { dialog = null },
        )
        is MonthDialog.Create -> EnvelopeFormDialog(
            initialPurpose = open.purpose,
            allowedPurposes = open.allowed,
            onSubmit = { envelope, result -> submit(result) { controller.createEnvelope(envelope); null } },
            onDismiss = { dialog = null },
        )
        MonthDialog.Income -> IncomeFormDialog(
            current = controller.dashboard?.income?.amount,
            onSubmit = { amount, result -> submit(result) { controller.saveIncome(amount); null } },
            onDismiss = { dialog = null },
        )
        MonthDialog.Glossary -> GlossaryDialog { dialog = null }
    }
}

@Composable
private fun NewEnvelopeButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(label) }
}

/** Convite e remoção de participantes — só o owner chega aqui. */
@Composable
private fun ParticipantsDialog(
    envelope: EnvelopeView,
    sharing: SharingGateway,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var items by remember(envelope.id) { mutableStateOf<List<ParticipantView>>(emptyList()) }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(envelope.id) {
        runCatching { sharing.listParticipants(envelope.id) }
            .onSuccess { items = it; error = null }
            .onFailure { error = describeFailure(it, "Não foi possível carregar os participantes.") }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Participantes de ${envelope.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Convidados veem a verba e registram gastos. Só você abastece, convida e encerra.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (items.isEmpty()) {
                    Text("Nenhum participante ainda.", style = MaterialTheme.typography.bodySmall)
                } else {
                    items.forEach { participant ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(participant.email, fontWeight = FontWeight.SemiBold)
                                TextButton(
                                    onClick = {
                                        if (busy) return@TextButton
                                        busy = true
                                        scope.launch {
                                            runCatching { sharing.removeParticipant(envelope.id, participant.userId) }
                                                .onSuccess {
                                                    items = items.filterNot { it.userId == participant.userId }
                                                    error = null
                                                }
                                                .onFailure {
                                                    error = describeFailure(it, "Não foi possível remover.")
                                                }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.height(TouchTarget),
                                ) { Text("Remover") }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail (conta já verificada)") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = email.trim()
                    if (trimmed.isEmpty() || busy) return@Button
                    busy = true
                    scope.launch {
                        runCatching { sharing.inviteParticipant(envelope.id, trimmed) }
                            .onSuccess { created ->
                                if (items.none { it.userId == created.userId }) {
                                    items = items + created
                                }
                                email = ""
                                error = null
                                onNotify("Participante convidado.")
                            }
                            .onFailure {
                                error = describeFailure(it, "Não foi possível convidar este e-mail.")
                            }
                        busy = false
                    }
                },
                enabled = !busy && email.trim().isNotEmpty(),
                modifier = Modifier.height(TouchTarget),
            ) { Text(if (busy) "Aguarde…" else "Convidar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.height(TouchTarget)) {
                Text("Fechar")
            }
        },
    )
}

/** Glossário das naturezas de verba, espelhando o da web. */
@Composable
internal fun GlossaryDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naturezas de verba", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(EnvelopePurpose.entries) { purpose ->
                    Column {
                        Text(purpose.label, fontWeight = FontWeight.Bold)
                        Text(
                            purpose.explanation,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.height(TouchTarget)) { Text("Entendi") }
        },
    )
}

/** Lançamentos de uma verba, para conferir o que já foi registrado no mês. */
@Composable
internal fun EnvelopeEntriesScreen(
    envelopeId: String,
    envelopeName: String,
    gateway: LedgerGateway,
    month: YearMonth,
    isCurrentMonth: Boolean,
) {
    var entries by remember(envelopeId, month) { mutableStateOf<List<LedgerEntryView>?>(null) }
    var error by remember(envelopeId, month) { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(envelopeId, month) {
        runCatching { gateway.listEntries(envelopeId, month.takeUnless { isCurrentMonth }) }
            .onSuccess { entries = it; error = null }
            .onFailure { error = describeFailure(it, "Não foi possível carregar os lançamentos.") }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("LANÇAMENTOS", envelopeName, month.label()) }
        when {
            error != null -> item { ErrorCard(error!!) }
            entries == null -> item { LoadingBox("Carregando lançamentos", Modifier.fillMaxWidth()) }
            entries!!.isEmpty() -> item { EmptyCard("Nenhum lançamento nesta verba no mês.") }
            else -> items(entries!!, key = LedgerEntryView::id) { EntryRow(it) }
        }
    }
}

@Composable
internal fun EntryRow(entry: LedgerEntryView) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                entry.amount.toBrl(),
                fontWeight = FontWeight.Bold,
                color = if (entry.kind == LedgerKind.CONTRIBUTION) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${entry.kind.label} · ${formatIsoDateBr(entry.occurredAt)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            entry.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
