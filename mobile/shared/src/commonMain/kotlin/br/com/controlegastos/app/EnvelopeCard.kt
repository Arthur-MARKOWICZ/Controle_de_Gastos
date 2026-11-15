package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
internal fun purposeColor(purpose: String): Color = purposeTone(purpose)

/**
 * Card de verba com as ações que a pessoa pode executar sobre ela.
 *
 * Uma ação primária fica visível e as demais vão para "Mais": cinco botões
 * numa linha não cabem em 360 dp e deixariam Encerrar encostado em Registrar.
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
    onParticipants: (() -> Unit)? = null,
    onRecurring: (() -> Unit)? = null,
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
                    contentDescription = if (envelope.isNegative) {
                        "Progresso de ${envelope.name}: saldo negativo"
                    } else {
                        "Progresso de ${envelope.name}: ${(progress * 100).toInt()}%"
                    }
                },
                color = if (envelope.isNegative) MaterialTheme.colorScheme.error else purposeColor(envelope.purpose),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            EnvelopeStatus(envelope)
            EnvelopeCardActionBar(
                envelope = envelope,
                onRegister = onRegister,
                onEdit = onEdit,
                onArchive = onArchive,
                onOpenEntries = onOpenEntries,
                onParticipants = onParticipants,
                onRecurring = onRecurring,
            )
        }
    }
}

@Composable
private fun EnvelopeCardActionBar(
    envelope: EnvelopeView,
    onRegister: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onArchive: (() -> Unit)?,
    onOpenEntries: (() -> Unit)?,
    onParticipants: (() -> Unit)?,
    onRecurring: (() -> Unit)?,
) {
    val actions = envelopeCardActions(envelope)
    val handlers = mapOf(
        EnvelopeAction.ENTRIES to onOpenEntries,
        EnvelopeAction.PARTICIPANTS to onParticipants,
        EnvelopeAction.RECURRING to onRecurring,
        EnvelopeAction.EDIT to onEdit,
        EnvelopeAction.ARCHIVE to onArchive,
    )
    val menu = actions.menu.mapNotNull { action -> handlers[action]?.let { action to it } }
    if (onRegister == null && menu.isEmpty()) return

    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onRegister?.let {
            FilledTonalButton(onClick = it, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text(actions.primaryLabel)
            }
        }
        Spacer(Modifier.weight(1f))
        if (menu.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(TouchTarget),
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Mais ações em ${envelope.name}")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    menu.forEach { (action, handler) ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                expanded = false
                                handler()
                            },
                            modifier = Modifier.heightIn(min = TouchTarget),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Estado da verba em texto, com ícone só de reforço.
 *
 * O texto é quem comunica: o ícone tem `contentDescription` nulo para o
 * leitor de tela não repetir o que a frase já diz.
 */
@Composable
private fun EnvelopeStatus(envelope: EnvelopeView) {
    val negative = envelope.isNegative
    val reached = !negative && envelope.targetReachedAt != null
    val color = when {
        negative -> MaterialTheme.colorScheme.error
        reached -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            negative -> Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            reached -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            statusLine(envelope),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}
