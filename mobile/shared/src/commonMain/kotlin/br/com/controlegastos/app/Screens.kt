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
import androidx.compose.material3.Text
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
    data class Register(val envelope: EnvelopeView) : MonthDialog
    data class Edit(val envelope: EnvelopeView) : MonthDialog
    data class Archive(val envelope: EnvelopeView) : MonthDialog
    data class Create(val purpose: EnvelopePurpose, val allowed: List<EnvelopePurpose>) : MonthDialog
    data object Income : MonthDialog
    data object Glossary : MonthDialog
}

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
    today: String,
    onOpenEntries: (EnvelopeView) -> Unit,
    onNotify: (String) -> Unit,
    onSessionExpired: () -> Unit = {},
) {
    var state by remember(controller) { mutableStateOf(controller.state) }
    var dialog by remember { mutableStateOf<MonthDialog?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() = scope.launch {
        controller.refresh()
        state = controller.state
        if (controller.lastFailure?.isSessionExpired() == true) onSessionExpired()
    }

    // O controlador é compartilhado pelas quatro abas de mês; recarregar a cada
    // troca faria quatro chamadas para os mesmos dados e piscaria a tela.
    androidx.compose.runtime.LaunchedEffect(controller) {
        if (controller.dashboard == null) reload()
    }

    /** Executa a escrita, devolve a mensagem de erro à tela e sincroniza o estado. */
    fun submit(result: FormResult, action: suspend () -> String?) {
        scope.launch {
            val outcome = runCatching { action() }
            state = controller.state
            if (outcome.exceptionOrNull()?.isSessionExpired() == true) onSessionExpired()
            outcome
                .onSuccess { celebration ->
                    result(null)
                    celebration?.let(onNotify)
                }
                .onFailure { result(describeFailure(it, "Não foi possível concluir a operação.")) }
        }
    }

    MonthScreen(
        controller = controller,
        state = state,
        onRefresh = { reload() },
        onPreviousMonth = { scope.launch { controller.showPreviousMonth(); state = controller.state } },
        onNextMonth = { scope.launch { controller.showNextMonth(); state = controller.state } },
    ) { dashboard ->
        when (route) {
            Route.Envelopes -> {
                item {
                    SectionHeader(
                        "LIMITES E COMPROMISSOS",
                        "Suas verbas",
                        "O que sobrar em uma verba acumula para o mês seguinte.",
                    )
                }
                item { NegativeBalanceAlert(dashboard.envelopes) }
                envelopeSection(
                    title = "Limites de gasto",
                    envelopes = dashboard.envelopes.filter { it.purpose == EnvelopePurpose.LIMIT.api },
                    emptyMessage = "Nenhum limite de gasto neste mês.",
                    onRegister = { dialog = MonthDialog.Register(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
                )
                envelopeSection(
                    title = "Compromissos fixos",
                    envelopes = dashboard.envelopes.filter { it.purpose == EnvelopePurpose.FIXED.api },
                    emptyMessage = "Nenhum compromisso fixo neste mês.",
                    onRegister = { dialog = MonthDialog.Register(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
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
                    onRegister = { dialog = MonthDialog.Register(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
                )
                envelopeSection(
                    title = "Metas de acumulação",
                    envelopes = dashboard.envelopes.filter { it.purpose == EnvelopePurpose.SAVINGS_TARGET.api },
                    emptyMessage = "Nenhuma meta de acumulação neste mês.",
                    onRegister = { dialog = MonthDialog.Register(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
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
                    onRegister = { dialog = MonthDialog.Register(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
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
                envelopeSection(
                    title = "Suas verbas",
                    envelopes = dashboard.envelopes,
                    emptyMessage = "Nenhuma verba neste mês. Crie a primeira para começar a reservar.",
                    onRegister = { dialog = MonthDialog.Register(it) },
                    onEdit = { dialog = MonthDialog.Edit(it) },
                    onArchive = { dialog = MonthDialog.Archive(it) },
                    onOpenEntries = onOpenEntries,
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
        is MonthDialog.Register -> EntryFormDialog(
            envelope = open.envelope,
            kind = if (open.envelope.purpose in setOf(EnvelopePurpose.GOAL.api, EnvelopePurpose.SAVINGS_TARGET.api)) {
                LedgerKind.CONTRIBUTION
            } else {
                LedgerKind.EXPENSE
            },
            today = today,
            onSubmit = { entry, result ->
                submit(result) {
                    val reached = controller.registerEntry(open.envelope.id, entry)
                    if (reached) "Meta de ${open.envelope.name} alcançada." else null
                }
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
            onConfirm = {
                dialog = null
                submit({ it?.let(onNotify) }) { controller.archiveEnvelope(open.envelope.id); null }
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
                "${entry.kind.label} · ${entry.occurredAt}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            entry.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
