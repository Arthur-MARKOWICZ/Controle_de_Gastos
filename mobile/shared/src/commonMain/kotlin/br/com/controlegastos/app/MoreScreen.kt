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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun MoreScreen(
    email: String,
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpen: (Route) -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionHeader("CONTA", "Mais", email) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    NavigationRow("Gastos anuais", "Provisões que vencem uma vez por ano") {
                        onOpen(Route.AnnualExpenses)
                    }
                    NavigationRow("Relatórios", "Exportar o período em planilha") { onOpen(Route.Reports) }
                    NavigationRow("Histórico de renda", "Como a renda mudou ao longo do tempo") {
                        onOpen(Route.IncomeHistory)
                    }
                    NavigationRow("Segurança", "Senha, MFA e contas conectadas") { onOpen(Route.Security) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aparência", fontWeight = FontWeight.Bold)
                    Text(
                        "A preferência fica só neste aparelho.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ChipRow(ThemeMode.entries, themeMode, onThemeSelected) { mode ->
                        when (mode) {
                            ThemeMode.SYSTEM -> "Sistema"
                            ThemeMode.LIGHT -> "Claro"
                            ThemeMode.DARK -> "Escuro"
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Sair da conta")
            }
        }
    }
}

@Composable
private fun NavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Histórico de alterações da renda, com quando cada valor passou a valer. */
@Composable
internal fun IncomeHistoryScreen(gateway: IncomeGateway) {
    var page by remember { mutableStateOf<IncomeHistoryPageView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun load(index: Int) = scope.launch {
        runCatching { gateway.loadIncomeHistory(index) }
            .onSuccess { page = it; pageIndex = index; error = null }
            .onFailure { error = describeFailure(it, "Não foi possível carregar o histórico de renda.") }
    }

    LaunchedEffect(gateway) { load(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("RENDA", "Histórico de renda", "Cada alteração e o mês em que passou a valer.") }
        when {
            error != null -> item { ErrorCard(error!!) { load(pageIndex) } }
            page == null -> item { LoadingBox("Carregando renda", Modifier.fillMaxWidth()) }
            page!!.items.isEmpty() -> item { EmptyCard("Nenhuma renda registrada até agora.") }
            else -> {
                items(page!!.items, key = IncomeHistoryItemView::id) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(entry.amount.toBrl(), fontWeight = FontWeight.Bold)
                            Text(
                                "Vale a partir de ${entry.effectiveFrom}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { load(pageIndex - 1) },
                            enabled = pageIndex > 0,
                            modifier = Modifier.height(TouchTarget),
                        ) { Text("‹ Anteriores") }
                        TextButton(
                            onClick = { load(pageIndex + 1) },
                            enabled = page!!.hasNext,
                            modifier = Modifier.height(TouchTarget),
                        ) { Text("Próximos ›") }
                    }
                }
            }
        }
    }
}
