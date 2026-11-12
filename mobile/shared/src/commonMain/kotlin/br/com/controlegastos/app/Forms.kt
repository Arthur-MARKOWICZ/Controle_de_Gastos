package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val MAX_NAME = 80
private const val MILLIS_PER_DAY = 86_400_000L

/** Resultado de uma submissão: `null` quando deu certo, senão a mensagem de erro. */
internal typealias FormResult = (String?) -> Unit

/** Criação de verba, com os campos que cada natureza exige. */
@Composable
internal fun EnvelopeFormDialog(
    initialPurpose: EnvelopePurpose,
    allowedPurposes: List<EnvelopePurpose> = EnvelopePurpose.entries,
    onSubmit: (NewEnvelope, FormResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var purpose by remember { mutableStateOf(initialPurpose) }
    var name by remember { mutableStateOf("") }
    var baseDigits by remember { mutableStateOf("") }
    var targetDigits by remember { mutableStateOf("") }
    var annualDigits by remember { mutableStateOf("") }
    var dueMonth by remember { mutableStateOf("1") }
    var dueDay by remember { mutableStateOf("1") }
    var fundingMode by remember { mutableStateOf(FundingMode.MONTHLY) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val annual = purpose == EnvelopePurpose.ANNUAL_EXPENSE
    val dueMonthValue = dueMonth.toIntOrNull()
    val dueDayValue = dueDay.toIntOrNull()
    val dueValid = !annual || (
        dueMonthValue in 1..12 &&
            dueDayValue != null &&
            dueDayValue in 1..YearMonth(2024, dueMonthValue ?: 1).lengthOfMonth()
        )
    val amountValid = if (annual) AmountInput.toMoney(annualDigits).cents > 0 else baseDigits.isNotEmpty()

    FormDialog(
        title = "Nova verba",
        confirmLabel = "Criar verba",
        confirmEnabled = name.isNotBlank() && amountValid && dueValid,
        busy = busy,
        message = message,
        onConfirm = {
            busy = true
            message = null
            onSubmit(
                NewEnvelope(
                    name = name.trim(),
                    purpose = purpose,
                    baseAmount = AmountInput.toMoney(baseDigits),
                    targetAmount = AmountInput.toMoney(targetDigits)
                        .takeIf { purpose == EnvelopePurpose.SAVINGS_TARGET && it.cents > 0 },
                    annualAmount = AmountInput.toMoney(annualDigits).takeIf { annual },
                    dueMonth = dueMonthValue.takeIf { annual },
                    dueDay = dueDayValue.takeIf { annual },
                    fundingMode = fundingMode.takeIf { annual },
                ),
            ) { error ->
                busy = false
                message = error
                if (error == null) onDismiss()
            }
        },
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (allowedPurposes.size > 1) {
                Text("Natureza", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allowedPurposes.chunked(2).forEach { row ->
                        ChipRow(row, purpose, { purpose = it }, EnvelopePurpose::label)
                    }
                }
            }
            Text(
                purpose.explanation,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (annual) {
                AmountField(annualDigits, { annualDigits = it }, "Valor anual")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                        label = { Text("Dia") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = dueMonth,
                        onValueChange = { dueMonth = it.filter(Char::isDigit).take(2) },
                        label = { Text("Mês") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Como reservar", style = MaterialTheme.typography.labelLarge)
                ChipRow(FundingMode.entries, fundingMode, { fundingMode = it }, FundingMode::label)
            } else {
                AmountField(
                    baseDigits,
                    { baseDigits = it },
                    if (purpose == EnvelopePurpose.GOAL) "Aporte planejado por mês" else "Valor reservado por mês",
                )
                if (purpose == EnvelopePurpose.SAVINGS_TARGET) {
                    AmountField(targetDigits, { targetDigits = it }, "Valor-alvo")
                }
            }
        }
    }
}

/** Edição de verba: só o que a natureza permite mudar. */
@Composable
internal fun EnvelopeEditDialog(
    envelope: EnvelopeView,
    onSubmit: (EnvelopeEdit, FormResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(envelope.name) }
    var baseDigits by remember { mutableStateOf(envelope.baseAmount.cents.toString()) }
    var targetDigits by remember { mutableStateOf((envelope.targetAmount?.cents ?: 0L).toString()) }
    var annualDigits by remember { mutableStateOf((envelope.annualExpense?.annualAmount?.cents ?: 0L).toString()) }
    var dueMonth by remember { mutableStateOf((envelope.annualExpense?.dueMonth ?: 1).toString()) }
    var dueDay by remember { mutableStateOf((envelope.annualExpense?.dueDay ?: 1).toString()) }
    var fundingMode by remember { mutableStateOf(envelope.annualExpense?.fundingMode ?: FundingMode.MONTHLY) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val annual = envelope.annualExpense != null

    FormDialog(
        title = "Editar ${envelope.name}",
        confirmLabel = "Salvar",
        confirmEnabled = name.isNotBlank(),
        busy = busy,
        message = message,
        onConfirm = {
            busy = true
            message = null
            val edit = if (annual) {
                EnvelopeEdit(
                    version = envelope.version,
                    name = name.trim().takeIf { it != envelope.name },
                    annualAmount = AmountInput.toMoney(annualDigits),
                    dueMonth = dueMonth.toIntOrNull(),
                    dueDay = dueDay.toIntOrNull(),
                    fundingMode = fundingMode,
                )
            } else {
                EnvelopeEdit(
                    version = envelope.version,
                    name = name.trim().takeIf { it != envelope.name },
                    baseAmount = AmountInput.toMoney(baseDigits),
                    targetAmount = AmountInput.toMoney(targetDigits)
                        .takeIf { envelope.purpose == EnvelopePurpose.SAVINGS_TARGET.api && it.cents > 0 },
                )
            }
            onSubmit(edit) { error ->
                busy = false
                message = error
                if (error == null) onDismiss()
            }
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME) },
            label = { Text("Nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (annual) {
            AmountField(annualDigits, { annualDigits = it }, "Valor anual")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                    label = { Text("Dia") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = dueMonth,
                    onValueChange = { dueMonth = it.filter(Char::isDigit).take(2) },
                    label = { Text("Mês") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            ChipRow(FundingMode.entries, fundingMode, { fundingMode = it }, FundingMode::label)
        } else {
            AmountField(baseDigits, { baseDigits = it }, "Valor reservado por mês")
            if (envelope.purpose == EnvelopePurpose.SAVINGS_TARGET.api) {
                AmountField(targetDigits, { targetDigits = it }, "Valor-alvo")
            }
        }
    }
}

/** Renda do mês corrente. */
@Composable
internal fun IncomeFormDialog(
    current: Money?,
    onSubmit: (Money, FormResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by remember { mutableStateOf((current?.cents ?: 0L).toString().takeIf { it != "0" } ?: "") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    FormDialog(
        title = "Renda do mês",
        confirmLabel = "Salvar renda",
        confirmEnabled = digits.isNotEmpty(),
        busy = busy,
        message = message,
        onConfirm = {
            busy = true
            message = null
            onSubmit(AmountInput.toMoney(digits)) { error ->
                busy = false
                message = error
                if (error == null) onDismiss()
            }
        },
        onDismiss = onDismiss,
    ) {
        AmountField(digits, { digits = it }, "Renda mensal")
        Text(
            "A renda vale a partir deste mês. Ela não pode ficar abaixo do total já reservado nas verbas.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun isIsoDate(value: String): Boolean =
    Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value) &&
        value.substring(5, 7).toInt() in 1..12 &&
        value.substring(8, 10).toInt() in 1..31

/** Data de leitura em pt-BR; o que não for `YYYY-MM-DD` volta como veio. */
internal fun formatIsoDateBr(iso: String): String =
    if (!isIsoDate(iso)) iso
    else "${iso.substring(8, 10)}/${iso.substring(5, 7)}/${iso.substring(0, 4)}"

internal fun previousIsoDay(iso: String): String {
    val year = iso.substring(0, 4).toInt()
    val month = iso.substring(5, 7).toInt()
    val day = iso.substring(8, 10).toInt()
    if (day > 1) return formatIsoParts(year, month, day - 1)
    val previous = YearMonth(year, month).previous()
    return previous.lastDay()
}

internal fun isoDateToUtcMillis(iso: String): Long {
    val year = iso.substring(0, 4).toInt()
    val month = iso.substring(5, 7).toInt()
    val day = iso.substring(8, 10).toInt()
    var days = 0L
    for (y in 1970 until year) {
        days += if (isGregorianLeap(y)) 366 else 365
    }
    val monthLengths = intArrayOf(31, if (isGregorianLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    for (m in 1 until month) days += monthLengths[m - 1]
    days += (day - 1)
    return days * MILLIS_PER_DAY
}

internal fun utcMillisToIsoDate(millis: Long): String {
    var days = millis / MILLIS_PER_DAY
    var year = 1970
    while (true) {
        val length = if (isGregorianLeap(year)) 366L else 365L
        if (days < length) break
        days -= length
        year++
    }
    val monthLengths = intArrayOf(31, if (isGregorianLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 1
    while (month <= 12 && days >= monthLengths[month - 1]) {
        days -= monthLengths[month - 1]
        month++
    }
    val day = days.toInt() + 1
    return formatIsoParts(year, month, day)
}

private fun formatIsoParts(year: Int, month: Int, day: Int): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

private fun isGregorianLeap(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
