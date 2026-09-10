package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Relatórios exportáveis do período.
 *
 * Dois deles são mensais e por isso o período é escolhido por mês inteiro, o
 * que também evita a validação de mês incompleto que a API recusa.
 */
@Composable
internal fun ReportsScreen(
    gateway: ReportGateway,
    currentMonth: YearMonth,
    onOpenReport: (DownloadedReport) -> Unit,
    onNotify: (String) -> Unit,
) {
    var from by remember { mutableStateOf(currentMonth) }
    var to by remember { mutableStateOf(currentMonth) }
    var format by remember { mutableStateOf(ReportFormat.XLSX) }
    var busy by remember { mutableStateOf<ReportId?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val invertedPeriod = from > to

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionHeader("EXPORTAR", "Relatórios", "Baixe o período em planilha para guardar ou enviar.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MonthRange("De", from, { from = it })
                    MonthRange("Até", to, { to = it })
                    if (invertedPeriod) {
                        Text(
                            "O mês inicial precisa vir antes do final.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Formato", style = MaterialTheme.typography.labelLarge)
                    ChipRow(ReportFormat.entries, format, { format = it }, ReportFormat::label)
                }
            }
        }
        message?.let { item { ErrorCard(it) } }
        items(ReportId.entries) { report ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(report.label, fontWeight = FontWeight.Bold)
                    Text(
                        report.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        enabled = busy == null && !invertedPeriod,
                        onClick = {
                            busy = report
                            message = null
                            scope.launch {
                                runCatching {
                                    gateway.downloadReport(report, from.firstDay(), to.lastDay(), format)
                                }
                                    .onSuccess {
                                        busy = null
                                        onNotify("${it.fileName} pronto para compartilhar.")
                                        onOpenReport(it)
                                    }
                                    .onFailure {
                                        busy = null
                                        message = describeFailure(it, "Não foi possível gerar o relatório.")
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(TouchTarget),
                    ) { Text(if (busy == report) "Gerando…" else "Baixar ${format.extension.uppercase()}") }
                }
            }
        }
    }
}

@Composable
private fun MonthRange(label: String, month: YearMonth, onChange: (YearMonth) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { onChange(month.previous()) }, modifier = Modifier.height(TouchTarget)) { Text("‹") }
            Text(month.label(), fontWeight = FontWeight.Bold)
            TextButton(onClick = { onChange(month.next()) }, modifier = Modifier.height(TouchTarget)) { Text("›") }
        }
    }
}
