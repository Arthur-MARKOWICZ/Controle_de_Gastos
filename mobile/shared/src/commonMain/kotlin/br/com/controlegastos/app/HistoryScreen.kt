package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface HistoryDialog {
    data class Edit(val item: HistoryItemView) : HistoryDialog
    data class Delete(val item: HistoryItemView) : HistoryDialog
    data class Revisions(val item: HistoryItemView) : HistoryDialog
}

/**
 * Histórico do período, com o lançamento rápido do mês que está na lista.
 *
 * A aba pode estar num mês diferente do painel, então a folha aberta daqui
 * usa [HistoryController.month] e avisa o mês escolhido em [onMonthChanged]
 * para a casca decidir se ainda oferece o atalho de novo gasto.
 */
@Composable
internal fun HistoryScreen(
    controller: HistoryController,
    envelopeGateway: EnvelopeGateway,
    today: String,
    onSessionExpired: () -> Unit = {},
    onMonthChanged: (YearMonth) -> Unit = {},
    onNotify: (String) -> Unit = {},
    quickExpenseRequested: Boolean = false,
    onQuickExpenseHandled: () -> Unit = {},
    onRegisterEntry: suspend (envelopeId: String, name: String, entry: NewLedgerEntry) -> String =
        { _, _, _ -> "" },
) {
    // A tela carrega as próprias verbas em vez de receber as do painel: o
    // histórico pode ser a primeira aba aberta, e aí o painel nunca carregou.
    var envelopes by remember(envelopeGateway) { mutableStateOf<List<EnvelopeView>>(emptyList()) }
    var state by remember(controller) { mutableStateOf(controller.state) }
    var dialog by remember { mutableStateOf<HistoryDialog?>(null) }
    var entrySheetOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reloading(action: suspend () -> Unit) = scope.launch {
        action()
        state = controller.state
        onMonthChanged(controller.month)
        if (controller.lastFailure?.isSessionExpired() == true) onSessionExpired()
    }

    LaunchedEffect(controller) { reloading { controller.refresh() } }
    LaunchedEffect(quickExpenseRequested) {
        if (quickExpenseRequested) {
            entrySheetOpen = true
            onQuickExpenseHandled()
        }
    }
    LaunchedEffect(envelopeGateway, controller.month) {
        envelopes = runCatching { envelopeGateway.listEnvelopes(controller.month) }.getOrDefault(envelopes)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionHeader("REGISTRO", "Gastos", "Tudo o que foi lançado no período.") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { reloading { controller.showPreviousMonth() } },
                    modifier = Modifier.height(TouchTarget),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null)
                    Text("Anterior")
                }
                Text(controller.month.label(), fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = { reloading { controller.showNextMonth() } },
                    modifier = Modifier.height(TouchTarget),
                ) {
                    Text("Próximo")
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = controller.includeDeleted,
                    onCheckedChange = { include -> reloading { controller.showDeleted(include) } },
                )
                Text("Mostrar lançamentos excluídos")
            }
        }
        when (val current = state) {
            HistoryState.Loading -> item { LoadingBox("Carregando histórico", Modifier.fillMaxWidth()) }
            is HistoryState.Error -> item { ErrorCard(current.message) { reloading { controller.refresh() } } }
            is HistoryState.Content -> {
                item { PeriodSummaryCard(current.summary) }
                if (current.summary.purposeTotals.isNotEmpty()) {
                    item { PurposeTotals(current.summary.purposeTotals) }
                }
                item {
                    Text("Lançamentos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (current.page.items.isEmpty()) {
                    item { EmptyCard("Nenhum lançamento neste período.") }
                } else {
                    items(current.page.items, key = { it.entry.id }) { item ->
                        HistoryRow(
                            item = item,
                            onRevisions = if (item.isOwner) {
                                { dialog = HistoryDialog.Revisions(item) }
                            } else {
                                null
                            },
                            onEdit = if (item.isOwner && item.entry.deletedAt == null) {
                                { dialog = HistoryDialog.Edit(item) }
                            } else {
                                null
                            },
                            onDelete = if (item.isOwner && item.entry.deletedAt == null) {
                                { dialog = HistoryDialog.Delete(item) }
                            } else {
                                null
                            },
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(
                                onClick = { reloading { controller.previousPage() } },
                                enabled = controller.page > 0,
                                modifier = Modifier.height(TouchTarget),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null)
                                Text("Anteriores")
                            }
                            TextButton(
                                onClick = { reloading { controller.nextPage() } },
                                enabled = current.page.hasNext,
                                modifier = Modifier.height(TouchTarget),
                            ) {
                                Text("Próximos")
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (entrySheetOpen) {
        val target = envelopes.expenseEnvelopes()
        EntrySheet(
            envelopes = target,
            initialEnvelopeId = null,
            kind = LedgerKind.EXPENSE,
            month = controller.month,
            today = today,
            onSubmit = { envelopeId, entry, result ->
                val name = target.firstOrNull { it.id == envelopeId }?.name ?: "verba"
                scope.launch {
                    runCatching { onRegisterEntry(envelopeId, name, entry) }
                        .onSuccess { notice ->
                            result(null)
                            notice.takeIf(String::isNotBlank)?.let(onNotify)
                            reloading { controller.refresh() }
                        }
                        .onFailure { result(describeFailure(it, "Não foi possível registrar o gasto.")) }
                }
            },
            onDismiss = { entrySheetOpen = false },
        )
    }

    when (val open = dialog) {
        null -> Unit
        is HistoryDialog.Edit -> EntryEditDialog(
            item = open.item,
            envelopes = envelopes,
            onSubmit = { edit, result ->
                scope.launch {
                    runCatching { controller.editEntry(open.item.entry.id, edit) }
                        .onSuccess { result(null); dialog = null }
                        .onFailure { result(describeFailure(it, "Não foi possível salvar o lançamento.")) }
                    state = controller.state
                }
            },
            onDismiss = { dialog = null },
        )
        is HistoryDialog.Delete -> ConfirmDialog(
            title = "Excluir lançamento?",
            message = "O valor volta para o saldo de ${open.item.envelopeName}. O registro continua visível ao " +
                "marcar \"mostrar lançamentos excluídos\".",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = {
                dialog = null
                reloading { controller.deleteEntry(open.item.entry.id) }
            },
            onDismiss = { dialog = null },
        )
        is HistoryDialog.Revisions -> EntryRevisionsDialog(
            item = open.item,
            load = { controller.listRevisions(open.item.entry.id) },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun PeriodSummaryCard(summary: HistorySummaryView) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Renda", summary.income.toBrl())
                Metric("Consumo", summary.expenses.toBrl())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Aplicado", summary.applied.toBrl())
                Metric("Saldo do período", summary.netBalance.toBrl(), summary.netBalance.isNegative)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Saldo acumulado", summary.accumulatedBalance.toBrl(), summary.accumulatedBalance.isNegative)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, negative: Boolean = false) = Column {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    Text(
        value,
        fontWeight = FontWeight.Bold,
        color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun PurposeTotals(totals: List<PurposeTotalView>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Gastos por natureza", fontWeight = FontWeight.Bold)
            totals.forEach { total ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(EnvelopePurpose.labelOf(total.purpose), style = MaterialTheme.typography.bodySmall)
                    Text(total.amount.toBrl(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItemView,
    onRevisions: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val deleted = item.entry.deletedAt != null
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (deleted) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(item.envelopeName, fontWeight = FontWeight.Bold)
                    Text(
                        "${item.entry.kind.label} · ${formatIsoDateBr(item.entry.occurredAt)}" +
                            if (item.isOwner) "" else " · Participante",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(item.entry.amount.toBrl(), fontWeight = FontWeight.Black)
            }
            item.entry.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            if (deleted) {
                Text(
                    "Excluído",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (onRevisions != null || onEdit != null || onDelete != null) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    onRevisions?.let {
                        TextButton(onClick = it, modifier = Modifier.height(TouchTarget)) { Text("Alterações") }
                    }
                    onEdit?.let {
                        TextButton(onClick = it, modifier = Modifier.height(TouchTarget)) { Text("Editar") }
                    }
                    onDelete?.let {
                        TextButton(onClick = it, modifier = Modifier.height(TouchTarget)) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRevisionsDialog(
    item: HistoryItemView,
    load: suspend () -> List<LedgerEntryRevisionView>,
    onDismiss: () -> Unit,
) {
    var items by remember(item.entry.id) { mutableStateOf<List<LedgerEntryRevisionView>?>(null) }
    var error by remember(item.entry.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.entry.id) {
        runCatching { load() }
            .onSuccess { items = it; error = null }
            .onFailure { error = describeFailure(it, "Não foi possível carregar as alterações.") }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alterações · ${item.envelopeName}", fontWeight = FontWeight.Bold) },
        text = {
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                items == null -> Text("Carregando…")
                items!!.isEmpty() -> Text("Nenhuma edição ou exclusão registrada ainda.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items!!.forEach { revision ->
                        Column {
                            Text(
                                if (revision.action == "DELETE") "Exclusão" else "Edição",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${revision.previousAmount.toBrl()}" +
                                    (revision.previousDescription?.let { " · $it" } ?: ""),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(TouchTarget)) { Text("Fechar") }
        },
    )
}

/** Edição de lançamento. A data é imutável na API e por isso não é oferecida. */
@Composable
private fun EntryEditDialog(
    item: HistoryItemView,
    envelopes: List<EnvelopeView>,
    onSubmit: (LedgerEntryEdit, FormResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by remember { mutableStateOf(item.entry.amount.cents.toString()) }
    var description by remember { mutableStateOf(item.entry.description.orEmpty()) }
    var envelopeId by remember { mutableStateOf(item.entry.envelopeId) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val movable = envelopes.filter(EnvelopeView::isOwner)

    FormDialog(
        title = "Editar lançamento",
        confirmLabel = "Salvar",
        confirmEnabled = AmountInput.toMoney(digits).cents > 0,
        busy = busy,
        message = message,
        onConfirm = {
            busy = true
            message = null
            onSubmit(
                LedgerEntryEdit(
                    envelopeId = envelopeId,
                    amount = AmountInput.toMoney(digits),
                    description = description.trim().takeIf(String::isNotBlank),
                ),
            ) { error ->
                busy = false
                message = error
            }
        },
        onDismiss = onDismiss,
    ) {
        AmountField(digits, { digits = it }, "Valor")
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(140) },
            label = { Text("Descrição (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (movable.size > 1) {
            Text("Verba", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                movable.chunked(2).forEach { row ->
                    ChipRow(row, movable.firstOrNull { it.id == envelopeId }, { envelopeId = it.id }, EnvelopeView::name)
                }
            }
        }
        Text(
            "A data do lançamento não pode ser alterada. Exclua e registre de novo se ela estiver errada.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
