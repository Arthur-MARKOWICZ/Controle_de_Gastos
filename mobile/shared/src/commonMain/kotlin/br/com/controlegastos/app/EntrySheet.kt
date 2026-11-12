package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val MAX_ENTRY_DESCRIPTION = 140

/**
 * Lançamento de gasto ou aporte numa única superfície.
 *
 * Escolher a verba era um diálogo e preencher era outro: duas superfícies em
 * cadeia para o gesto mais repetido do aplicativo. A folha reúne verba, valor,
 * data e descrição, e cancelar é só fechá-la.
 *
 * O mês vem da tela que abriu a folha — o painel e a aba Gastos navegam por
 * meses diferentes —, por isso ele aparece escrito e a data fica presa nele.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySheet(
    envelopes: List<EnvelopeView>,
    initialEnvelopeId: String?,
    kind: LedgerKind,
    month: YearMonth,
    today: String,
    onSubmit: (envelopeId: String, entry: NewLedgerEntry, result: FormResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var envelopeId by remember {
        mutableStateOf(initialEnvelopeId ?: envelopes.singleOrNull()?.id)
    }
    var digits by remember { mutableStateOf("") }
    var occurredAt by remember { mutableStateOf(defaultOccurredAt(month, today).orEmpty()) }
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val selected = envelopes.firstOrNull { it.id == envelopeId }
    val amount = AmountInput.toMoney(digits)
    val dateValid = isSelectableEntryDate(occurredAt, month, today)
    val contribution = kind == LedgerKind.CONTRIBUTION

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    sheetTitle(contribution, selected, envelopes.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Lançamento em ${month.label()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (envelopes.isEmpty()) {
                Text(
                    "Nenhuma verba de limite, compromisso fixo ou gasto anual em ${month.label()}. " +
                        "Crie uma verba antes de lançar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(TouchTarget),
                ) { Text("Fechar") }
                return@Column
            }

            if (envelopes.size > 1) {
                Text("Verba", style = MaterialTheme.typography.labelLarge)
                envelopes.forEach { option ->
                    EnvelopeOption(
                        envelope = option,
                        selected = option.id == envelopeId,
                        onSelect = { envelopeId = option.id },
                    )
                }
            }

            AmountField(digits, { digits = it }, if (contribution) "Valor do aporte" else "Valor do gasto")

            // Aporte não consome saldo: a prévia é só do gasto.
            if (!contribution && selected != null && amount.cents > 0) {
                BalancePreview(balanceAfterExpense(selected.available, amount))
            }

            EntrySheetDateField(
                value = occurredAt,
                month = month,
                today = today,
                onChange = { occurredAt = it },
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(MAX_ENTRY_DESCRIPTION) },
                label = { Text("Descrição (opcional)") },
                supportingText = { Text("${description.length}/$MAX_ENTRY_DESCRIPTION") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = it
                    },
                )
            }

            Button(
                enabled = !busy && selected != null && amount.cents > 0 && dateValid,
                onClick = {
                    val target = selected ?: return@Button
                    busy = true
                    message = null
                    onSubmit(
                        target.id,
                        NewLedgerEntry(
                            kind = kind,
                            amount = amount,
                            occurredAt = occurredAt,
                            description = description.trim().takeIf(String::isNotBlank),
                        ),
                    ) { error ->
                        busy = false
                        message = error
                        if (error == null) onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "Aguarde…" else "Registrar") }
        }
    }
}

/**
 * Saldo previsto da verba se este gasto for registrado.
 *
 * Ficar negativo é permitido e vira alerta, então a prévia avisa sem travar o
 * botão de registrar.
 */
@Composable
private fun BalancePreview(preview: Money) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Saldo após o gasto: ${preview.toBrl()}",
            color = if (preview.isNegative) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (preview.isNegative) {
            Text(
                "A verba fica negativa. O registro continua permitido.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun sheetTitle(contribution: Boolean, selected: EnvelopeView?, options: Int): String = when {
    contribution && selected != null -> "Aportar em ${selected.name}"
    contribution -> "Novo aporte"
    options == 1 && selected != null -> "Gasto em ${selected.name}"
    else -> "Novo gasto"
}

@Composable
private fun EnvelopeOption(envelope: EnvelopeView, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = TouchTarget)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(envelope.name, fontWeight = FontWeight.SemiBold)
            Text(
                "Disponível ${envelope.available.toBrl()}",
                color = if (envelope.isNegative) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Data do lançamento dentro do mês exibido.
 *
 * Os atalhos aparecem só quando caem no mês visto: num mês passado "Hoje" não
 * é uma data possível.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheetDateField(
    value: String,
    month: YearMonth,
    today: String,
    onChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val lastDay = lastSelectableEntryDay(month, today)
    val shortcuts = entryDateShortcuts(month, today)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Data", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth().height(TouchTarget),
        ) {
            Text(if (isIsoDate(value)) formatIsoDateBr(value) else "Escolher data")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.forEach { (label, date) ->
                TextButton(onClick = { onChange(date) }, modifier = Modifier.height(TouchTarget)) {
                    Text(label)
                }
            }
        }
        if (!isSelectableEntryDate(value, month, today)) {
            Text(
                "Escolha um dia de ${month.label()} que já aconteceu.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = isoDateToUtcMillis(value.ifBlank { lastDay }),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    isSelectableEntryDate(utcMillisToIsoDate(utcTimeMillis), month, today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onChange(utcMillisToIsoDate(it)) }
                        showPicker = false
                    },
                    modifier = Modifier.height(TouchTarget),
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }, modifier = Modifier.height(TouchTarget)) {
                    Text("Cancelar")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
