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
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

@Composable
internal fun MoreScreen(
    email: String,
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpen: (Route) -> Unit,
    reminder: ReminderSettingsController,
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
                    Text(
                        "Planejamento",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    NavigationRow("Metas", "Acompanhe o quanto já foi guardado") {
                        onOpen(Route.Goals)
                    }
                    NavigationRow("Gastos anuais", "Provisões que vencem uma vez por ano") {
                        onOpen(Route.AnnualExpenses)
                    }
                    NavigationRow("Relatórios", "Exportar o período em planilha") { onOpen(Route.Reports) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        "Patrimônio",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    NavigationRow("Carteira", "Ações B3 e CDBs separados do orçamento") {
                        onOpen(Route.Carteira)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        "Conta",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    NavigationRow("Segurança", "Senha, MFA e contas conectadas") { onOpen(Route.Security) }
                    NavigationRow("Histórico de renda", "Como a renda mudou ao longo do tempo") {
                        onOpen(Route.IncomeHistory)
                    }
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
        item { DailyReminderCard(reminder) }
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
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Lembrete diário local (spec ui-mobile/lembrete-diario). Horário e consentimento
 * ficam na API; os outros avisos aparecem só dentro do app neste canal.
 */
@Composable
internal fun DailyReminderCard(reminder: ReminderSettingsController) {
    var state by remember(reminder) { mutableStateOf(reminder.state) }
    var choosingTime by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(action: suspend () -> Unit) = scope.launch {
        action()
        state = reminder.state
    }

    LaunchedEffect(reminder) {
        reminder.load()
        state = reminder.state
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = state.enabled,
                    enabled = state.preferences != null && !state.busy,
                    role = Role.Switch,
                    onValueChange = { enabled -> run { reminder.setEnabled(enabled) } },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Lembrete diário", fontWeight = FontWeight.Bold)
                    Text(
                        "Avisa no horário escolhido se você ainda não lançou nenhum gasto no dia.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = null)
            }
            Box {
                TextButton(
                    onClick = { choosingTime = true },
                    enabled = state.preferences != null && !state.busy,
                    modifier = Modifier.height(TouchTarget),
                ) { Text("Horário: ${state.time}") }
                DropdownMenu(expanded = choosingTime, onDismissRequest = { choosingTime = false }) {
                    REMINDER_TIMES.forEach { time ->
                        DropdownMenuItem(
                            text = { Text(time) },
                            onClick = {
                                choosingTime = false
                                run { reminder.setTime(time) }
                            },
                        )
                    }
                }
            }
            Text(
                "Horário de Brasília, o mesmo da web e do desktop. O aviso pode atrasar alguns minutos. " +
                    "Alertas de verba negativa e de verba compartilhada aparecem dentro do app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.permissionDenied) {
                Text(
                    "As notificações estão bloqueadas para o Verbas. Libere em Configurações do Android › Apps › " +
                        "Verbas › Notificações; o lembrete fica salvo e passa a valer quando você liberar.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
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
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null)
                            Text("Anteriores")
                        }
                        TextButton(
                            onClick = { load(pageIndex + 1) },
                            enabled = page!!.hasNext,
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
