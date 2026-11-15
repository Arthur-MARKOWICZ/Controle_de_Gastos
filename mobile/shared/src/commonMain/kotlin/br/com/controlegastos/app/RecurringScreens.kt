package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pendências de recorrência na tela inicial (ADR-0031): confirmar custa um toque.
 * Vencidas ficam num grupo próprio, nomeado em texto, não só por cor.
 */
@Composable
internal fun PendingOccurrencesSection(
    pending: PendingOccurrencesView,
    error: String?,
    busyId: String?,
    onConfirm: (PendingOccurrenceView) -> Unit,
    onAdjust: (PendingOccurrenceView) -> Unit,
    onDiscard: (PendingOccurrenceView) -> Unit,
) {
    if (pending.current.isEmpty() && pending.overdue.isEmpty() && error == null) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Recorrências para confirmar",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Nada entra no saldo antes de você confirmar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        if (pending.current.isNotEmpty()) {
            Text("Pendências do mês", fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            pending.current.forEach { PendingCard(it, false, busyId == it.id, onConfirm, onAdjust, onDiscard) }
        }
        if (pending.overdue.isNotEmpty()) {
            Text("Vencidas", fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            pending.overdue.forEach { PendingCard(it, true, busyId == it.id, onConfirm, onAdjust, onDiscard) }
        }
    }
}

@Composable
private fun PendingCard(
    item: PendingOccurrenceView,
    overdue: Boolean,
    busy: Boolean,
    onConfirm: (PendingOccurrenceView) -> Unit,
    onAdjust: (PendingOccurrenceView) -> Unit,
    onDiscard: (PendingOccurrenceView) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(item.suggestedAmount.toBrl(), fontWeight = FontWeight.Black)
            }
            Text(
                "${item.envelopeName} · ${if (overdue) "venceu em" else "previsto para"} ${formatIsoDateBr(item.dueDate)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onConfirm(item) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = TouchTarget).semantics {
                        contentDescription = "Confirmar ${item.label}"
                    },
                ) { Text(if (busy) "Aguarde…" else "Confirmar") }
                OutlinedButton(
                    onClick = { onAdjust(item) },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = TouchTarget).semantics { contentDescription = "Ajustar ${item.label}" },
                ) { Text("Ajustar") }
                TextButton(
                    onClick = { onDiscard(item) },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = TouchTarget).semantics { contentDescription = "Descartar ${item.label}" },
                ) { Text("Descartar") }
            }
        }
    }
}

/** Ajuste de valor e data antes de confirmar; o valor da recorrência é só sugestão. */
@Composable
internal fun AdjustOccurrenceDialog(
    item: PendingOccurrenceView,
    today: String,
    onSubmit: (Money, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by remember(item.id) { mutableStateOf(item.suggestedAmount.cents.toString()) }
    var date by remember(item.id) { mutableStateOf(item.dueDate) }
    val amount = AmountInput.toMoney(digits)
    val dateValid = isIsoDate(date) && date <= today
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar ${item.label}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField(digits = digits, onDigitsChange = { digits = it }, label = "Valor")
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Data (AAAA-MM-DD)") },
                    isError = !dateValid,
                    supportingText = { if (!dateValid) Text("Use uma data até hoje.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(amount, date) },
                enabled = amount.cents > 0 && dateValid,
                modifier = Modifier.height(TouchTarget),
            ) { Text("Confirmar com ajuste") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(TouchTarget)) { Text("Cancelar") }
        },
    )
}

/** Recorrências da verba: só o dono chega aqui (ADR-0031). */
@Composable
internal fun RecurringEntriesDialog(
    envelope: EnvelopeView,
    gateway: RecurringGateway,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val kind = recurringKindFor(envelope.purpose)
    var items by remember(envelope.id) { mutableStateOf<List<RecurringEntryView>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(envelope.id) {
        runCatching { gateway.listRecurring(envelope.id) }
            .onSuccess { items = it; error = null }
            .onFailure { error = describeFailure(it, "Não foi possível carregar as recorrências.") }
    }

    fun clearForm() {
        editingId = null; description = ""; digits = ""; day = ""
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { action() }.onFailure { error = describeFailure(it, "Não foi possível salvar a recorrência.") }
            busy = false
        }
    }

    fun replace(updated: RecurringEntryView) {
        items = items.orEmpty().map { if (it.id == updated.id) updated else it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recorrências de ${envelope.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Todo mês, no dia escolhido, você recebe uma pendência para confirmar, ajustar ou descartar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
                when {
                    items == null && error == null -> Text("Carregando…", style = MaterialTheme.typography.bodySmall)
                    items.orEmpty().isEmpty() -> Text("Nenhuma recorrência ainda.", style = MaterialTheme.typography.bodySmall)
                }
                items.orEmpty().forEach { entry ->
                    val label = entry.description ?: "Recorrência do dia ${entry.dayOfMonth}"
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Text(recurringSummary(entry), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (pendingDelete == entry.id) {
                                    TextButton(onClick = { pendingDelete = null }, enabled = !busy,
                                        modifier = Modifier.height(TouchTarget)) { Text("Manter") }
                                    TextButton(
                                        onClick = {
                                            run {
                                                gateway.deleteRecurring(envelope.id, entry.id)
                                                items = items.orEmpty().filterNot { it.id == entry.id }
                                                pendingDelete = null
                                                onNotify("Recorrência excluída.")
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.height(TouchTarget),
                                    ) { Text("Excluir de vez") }
                                } else {
                                    TextButton(
                                        onClick = {
                                            editingId = entry.id
                                            description = entry.description.orEmpty()
                                            digits = entry.amount.cents.toString()
                                            day = entry.dayOfMonth.toString()
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.height(TouchTarget),
                                    ) { Text("Editar") }
                                    TextButton(
                                        onClick = {
                                            run {
                                                replace(gateway.updateRecurring(envelope.id, entry.id,
                                                    RecurringEntryInput(entry.kind, entry.amount, entry.dayOfMonth, entry.description),
                                                    !entry.active))
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.height(TouchTarget),
                                    ) { Text(if (entry.active) "Desativar" else "Reativar") }
                                    TextButton(onClick = { pendingDelete = entry.id }, enabled = !busy,
                                        modifier = Modifier.height(TouchTarget)) { Text("Excluir") }
                                }
                            }
                        }
                    }
                }
                Text(
                    if (editingId != null) "Editar recorrência"
                    else "Nova recorrência de ${if (kind == LedgerKind.CONTRIBUTION) "aporte" else "gasto"}",
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(140) },
                    label = { Text("Descrição (opcional)") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                AmountField(digits = digits, onDigitsChange = { digits = it }, label = "Valor sugerido",
                    hint = "Na confirmação você pode ajustar.", enabled = !busy)
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("Dia do mês") },
                    supportingText = shortMonthHint(day.toIntOrNull())?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = AmountInput.toMoney(digits)
                    validateRecurringInput(amount, day)?.let { error = it; return@Button }
                    val input = RecurringEntryInput(kind, amount, day.trim().toInt(), description.trim().ifBlank { null })
                    val editing = editingId
                    run {
                        if (editing != null) {
                            val current = items.orEmpty().firstOrNull { it.id == editing }
                            replace(gateway.updateRecurring(envelope.id, editing, input, current?.active ?: true))
                        } else {
                            items = items.orEmpty() + gateway.createRecurring(envelope.id, input)
                            onNotify("Recorrência criada.")
                        }
                        clearForm()
                    }
                },
                enabled = !busy,
                modifier = Modifier.height(TouchTarget),
            ) { Text(if (editingId != null) "Salvar" else "Adicionar") }
        },
        dismissButton = {
            TextButton(
                onClick = { if (editingId != null) clearForm() else onDismiss() },
                enabled = !busy,
                modifier = Modifier.height(TouchTarget),
            ) { Text(if (editingId != null) "Cancelar edição" else "Fechar") }
        },
    )
}
