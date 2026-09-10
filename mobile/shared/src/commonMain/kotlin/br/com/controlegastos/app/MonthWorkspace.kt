package br.com.controlegastos.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Estrutura comum das telas que trabalham sobre um mês. */
@Composable
internal fun MonthScreen(
    controller: FinanceDashboardController,
    state: DashboardState,
    onRefresh: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    content: LazyListScope.(FinancialDashboard) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { MonthSelector(controller.month, controller.isCurrentMonth, onPreviousMonth, onNextMonth) }
        when (state) {
            DashboardState.Loading -> item { LoadingBox("Carregando verbas", Modifier.fillMaxWidth()) }
            is DashboardState.Error -> item { ErrorCard(state.message, onRefresh) }
            is DashboardState.Content -> content(state.dashboard)
        }
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, modifier = Modifier.height(TouchTarget)) { Text("‹ Anterior") }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(month.label(), fontWeight = FontWeight.Bold)
            if (isCurrentMonth) {
                Text(
                    "Mês atual",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        TextButton(onClick = onNext, modifier = Modifier.height(TouchTarget)) { Text("Próximo ›") }
    }
}

@Composable
internal fun IncomeSummaryCard(dashboard: FinancialDashboard, onConfigureIncome: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).height(if (onConfigureIncome == null) 148.dp else 196.dp)
                .background(MaterialTheme.colorScheme.primary))
            Column(Modifier.weight(1f).padding(20.dp)) {
                Text(
                    "Renda do mês",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    dashboard.income?.amount?.toBrl() ?: "Renda não configurada",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryValue("Reservado", dashboard.allocated.toBrl())
                    SummaryValue("Não alocado", dashboard.unallocated.toBrl())
                }
                Spacer(Modifier.height(12.dp))
                val usage = (dashboard.usagePct / 100.0).coerceIn(0.0, 1.0).toFloat()
                LinearProgressIndicator(
                    progress = { usage },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    "${dashboard.usagePct.toInt()}% da renda já está reservada",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                onConfigureIncome?.let {
                    OutlinedButton(
                        onClick = it,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(TouchTarget),
                    ) { Text(if (dashboard.income == null) "Configurar renda" else "Alterar renda") }
                }
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) = Column {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    Text(value, fontWeight = FontWeight.Bold)
}

/** Aviso agregado de verbas negativas, exibido antes da lista. */
@Composable
internal fun NegativeBalanceAlert(envelopes: List<EnvelopeView>) {
    val negative = envelopes.filter(EnvelopeView::isNegative)
    if (negative.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (negative.size == 1) "1 verba está negativa" else "${negative.size} verbas estão negativas",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                negative.joinToString(", ") { it.name },
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun LazyListScope.envelopeSection(
    title: String,
    envelopes: List<EnvelopeView>,
    emptyMessage: String,
    onRegister: (EnvelopeView) -> Unit,
    onEdit: (EnvelopeView) -> Unit,
    onArchive: (EnvelopeView) -> Unit,
    onOpenEntries: (EnvelopeView) -> Unit,
) {
    item { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
    if (envelopes.isEmpty()) {
        item { EmptyCard(emptyMessage) }
        return
    }
    items(envelopes, key = EnvelopeView::id) { envelope ->
        EnvelopeCard(
            envelope = envelope,
            onRegister = { onRegister(envelope) },
            onOpenEntries = { onOpenEntries(envelope) },
            onEdit = if (envelope.isOwner) ({ onEdit(envelope) }) else null,
            onArchive = if (envelope.isOwner) ({ onArchive(envelope) }) else null,
        )
    }
}
