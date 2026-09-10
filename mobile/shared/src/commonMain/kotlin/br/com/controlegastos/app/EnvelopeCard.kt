package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Progresso mostrado na barra da verba.
 *
 * Metas usam o percentual calculado pelo servidor. Nas demais, a razão entre
 * saldo e base é só apresentação: nenhum valor daqui volta para a API.
 */
internal fun progressOf(envelope: EnvelopeView): Float {
    envelope.goalProgress?.takeIf { envelope.purpose == EnvelopePurpose.GOAL.api }
        ?.let { return it.percent.coerceIn(0, 100) / 100f }
    envelope.targetAmount?.takeIf { it.cents > 0 }
        ?.let { return (envelope.available.cents.toFloat() / it.cents).coerceIn(0f, 1f) }
    if (envelope.baseAmount.cents <= 0) return 0f
    return (envelope.available.cents.toFloat() / envelope.baseAmount.cents).coerceIn(0f, 1f)
}

@Composable
internal fun purposeColor(purpose: String): Color = when (purpose) {
    EnvelopePurpose.GOAL.api, EnvelopePurpose.SAVINGS_TARGET.api -> MaterialTheme.colorScheme.tertiary
    EnvelopePurpose.FIXED.api, EnvelopePurpose.ANNUAL_EXPENSE.api -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

/**
 * Card de verba com as ações que a pessoa pode executar sobre ela.
 *
 * Participantes só registram lançamentos: abastecer, editar e encerrar são do
 * dono, e o backend recusa o resto com 403.
 */
@Composable
internal fun EnvelopeCard(
    envelope: EnvelopeView,
    onRegister: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onOpenEntries: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(envelope.name, fontWeight = FontWeight.Bold)
                    Text(
                        envelope.purposeLabel + if (envelope.isOwner) "" else " · Participante",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(headlineAmount(envelope), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            val progress = progressOf(envelope)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(7.dp).semantics {
                    contentDescription = "Progresso de ${envelope.name}: ${(progress * 100).toInt()}%"
                },
                color = if (envelope.isNegative) MaterialTheme.colorScheme.error else purposeColor(envelope.purpose),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                statusLine(envelope),
                color = if (envelope.isNegative) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (onRegister != null || onEdit != null || onArchive != null || onOpenEntries != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    onRegister?.let {
                        TextButton(onClick = it, modifier = Modifier.height(TouchTarget)) {
                            Text(if (isGoal(envelope)) "Aportar" else "Registrar")
                        }
                    }
                    onOpenEntries?.let {
                        TextButton(onClick = it, modifier = Modifier.height(TouchTarget)) { Text("Lançamentos") }
                    }
                    onEdit?.let {
                        TextButton(onClick = it, modifier = Modifier.height(TouchTarget)) { Text("Editar") }
                    }
                    onArchive?.let {
                        TextButton(onClick = it, modifier = Modifier.height(TouchTarget)) { Text("Encerrar") }
                    }
                }
            }
        }
    }
}

private fun isGoal(envelope: EnvelopeView): Boolean =
    envelope.purpose == EnvelopePurpose.GOAL.api || envelope.purpose == EnvelopePurpose.SAVINGS_TARGET.api

private fun headlineAmount(envelope: EnvelopeView): String {
    val goal = envelope.goalProgress
    if (goal != null && envelope.purpose == EnvelopePurpose.GOAL.api) {
        return "Faltam ${goal.remainingAmount.toBrl()}"
    }
    return envelope.available.toBrl()
}

private fun statusLine(envelope: EnvelopeView): String {
    val goal = envelope.goalProgress
    return when {
        envelope.isNegative -> "⚠ Saldo negativo. O gasto foi registrado mesmo assim."
        envelope.targetReachedAt != null -> "✓ Meta alcançada"
        goal != null && envelope.purpose == EnvelopePurpose.GOAL.api ->
            "Meta ${goal.plannedAmount.toBrl()} · ${goal.percent}% concluído"
        envelope.targetAmount != null -> "Alvo ${envelope.targetAmount.toBrl()}"
        envelope.annualExpense != null -> annualLine(envelope.annualExpense)
        else -> "Saldo disponível"
    }
}

private fun annualLine(annual: AnnualExpenseView): String {
    val day = annual.dueDay.toString().padStart(2, '0')
    val month = annual.dueMonth.toString().padStart(2, '0')
    return "${annual.annualAmount.toBrl()} por ano · vence em $day/$month · ${annual.fundingMode.label.lowercase()}"
}
