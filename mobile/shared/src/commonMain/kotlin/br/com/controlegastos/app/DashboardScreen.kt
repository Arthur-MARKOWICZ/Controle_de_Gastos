package br.com.controlegastos.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    email: String,
    financeGateway: FinanceGateway,
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onLogout: () -> Unit,
    onOpenSecuritySettings: () -> Unit = {},
) {
    val controller = remember(financeGateway) { FinanceDashboardController(financeGateway) }
    var dashboardState by remember { mutableStateOf<DashboardState>(DashboardState.Loading) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller) { controller.refresh(); dashboardState = controller.state }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Verbas", fontWeight = FontWeight.Black)
                        Text(
                            email,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    ThemeMenu(themeMode, onThemeSelected)
                    TextButton(onClick = onOpenSecuritySettings, modifier = Modifier.height(48.dp)) { Text("Segurança") }
                    TextButton(onClick = onLogout, modifier = Modifier.height(48.dp)) { Text("Sair") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text("VISÃO ATUAL", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Seu mês em números", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Confira o que já está reservado antes do próximo gasto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (val current = dashboardState) {
                DashboardState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = "Carregando verbas" })
                    }
                }
                is DashboardState.Error -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(current.message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { scope.launch { controller.refresh(); dashboardState = controller.state } }, modifier = Modifier.height(48.dp)) { Text("Tentar novamente") }
                        }
                    }
                }
                is DashboardState.Content -> {
                    item { IncomeSummary(current.dashboard) }
                    item { Text("Suas verbas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (current.dashboard.envelopes.isEmpty()) item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Text("Nenhuma verba disponível neste mês.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(20.dp))
                        }
                    }
                    items(current.dashboard.envelopes) { EnvelopeSummaryCard(it) }
                    item {
                        Button(
                            onClick = { scope.launch { controller.refresh(); dashboardState = controller.state } },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Atualizar verbas") }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeSummary(dashboard: FinancialDashboard) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).height(148.dp).background(MaterialTheme.colorScheme.primary))
            Column(Modifier.weight(1f).padding(20.dp)) {
                Text("Renda do mês", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Text(dashboard.income?.let(::formatBrl) ?: "Renda não configurada", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryValue("Reservado", formatBrl(dashboard.allocated))
                    SummaryValue("Não alocado", formatBrl(dashboard.unallocated))
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

@Composable
private fun EnvelopeSummaryCard(envelope: EnvelopeView) {
    val goalProgress = envelope.goalProgress.takeIf { envelope.purpose == "GOAL" }
    val purposeColor: Color = when (envelope.purpose) {
        "GOAL" -> MaterialTheme.colorScheme.tertiary
        "FIXED" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(envelope.name, fontWeight = FontWeight.Bold)
                    Text(purposeLabel(envelope.purpose), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (goalProgress == null) formatBrl(envelope.available) else "Faltam ${formatBrl(goalProgress.remainingAmount)}",
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(12.dp))
            val progress = progressOf(envelope)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(7.dp).semantics { contentDescription = "Progresso de ${envelope.name}: ${(progress * 100).toInt()}%" },
                color = if (envelope.isNegative) MaterialTheme.colorScheme.error else purposeColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    envelope.isNegative -> "⚠ Saldo negativo"
                    goalProgress != null -> "Meta acumulada ${formatBrl(goalProgress.plannedAmount)} · ${goalProgress.percent}% concluído"
                    else -> "Saldo disponível"
                },
                color = if (envelope.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatBrl(amount: MoneyView): String = "R$ ${amount.amount.replace('.', ',')}"
private fun purposeLabel(purpose: String): String = when (purpose) { "LIMIT" -> "Limite de gasto"; "GOAL" -> "Meta de aporte"; "FIXED" -> "Compromisso fixo"; else -> purpose }
internal fun progressOf(envelope: EnvelopeView): Float = envelope.goalProgress.takeIf { envelope.purpose == "GOAL" }
    ?.let { it.percent.coerceIn(0, 100) / 100f }
    ?: envelope.baseAmount.amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { (envelope.available.amount.toDoubleOrNull() ?: 0.0).div(it).coerceIn(0.0, 1.0).toFloat() } ?: 0f
