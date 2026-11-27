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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface PortfolioDeleteTarget {
    val label: String

    data class StockLot(val id: String, override val label: String) : PortfolioDeleteTarget
    data class Cdb(val id: String, override val label: String) : PortfolioDeleteTarget
}

/**
 * Hub de Carteira no app mobile (ADR-0034).
 *
 * Isolado do orçamento mensal: lê apenas `/api/v1/investments/summary` e
 * cadastra/exclui posições da carteira do próprio usuário autenticado.
 */
@Composable
internal fun CarteiraScreen(
    gateway: InvestmentsGateway,
    onNotify: (String) -> Unit = {},
) {
    var summary by remember { mutableStateOf<PortfolioSummaryView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showStockDialog by remember { mutableStateOf(false) }
    var showCdbDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PortfolioDeleteTarget?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() = scope.launch {
        loading = true
        error = null
        runCatching { gateway.loadSummary() }
            .onSuccess { summary = it }
            .onFailure { error = describeFailure(it, "Não foi possível carregar sua carteira.") }
        loading = false
    }

    LaunchedEffect(gateway) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                overline = "PATRIMÔNIO PRÓPRIO",
                title = "Carteira",
                description = "Ações B3 e CDBs separados do orçamento mensal.",
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showStockDialog = true },
                    modifier = Modifier.weight(1f).height(TouchTarget),
                ) { Text("Cadastrar ação") }
                OutlinedButton(
                    onClick = { showCdbDialog = true },
                    modifier = Modifier.weight(1f).height(TouchTarget),
                ) { Text("Cadastrar CDB") }
            }
        }

        when {
            error != null -> item { ErrorCard(error!!) { reload() } }
            loading && summary == null -> item { LoadingBox("Carregando carteira", Modifier.fillMaxWidth()) }
            summary != null && summary!!.stocks.isEmpty() && summary!!.cdbs.isEmpty() -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Ainda não há posições cadastradas", fontWeight = FontWeight.Bold)
                            Text(
                                "Comece cadastrando um lote de ação B3 ou um CDB prefixado. " +
                                    "Esses registros não criam lançamentos nas suas verbas.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            summary != null -> {
                val data = summary!!
                if (!data.complete) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text(
                                "Resumo parcial: uma ou mais ações ainda não têm cotação disponível.",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }
                }
                item { PortfolioTotalsCard(data) }

                if (data.stocks.isNotEmpty()) {
                    item {
                        Text(
                            "Ações",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(data.stocks, key = PortfolioStockPositionView::ticker) { stock ->
                        StockPositionCard(stock) { lot ->
                            deleteTarget = PortfolioDeleteTarget.StockLot(
                                id = lot.id,
                                label = "o lote de ${stock.ticker} (${lot.quantity} em ${lot.purchaseDate})",
                            )
                        }
                    }
                }

                if (data.cdbs.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                "CDBs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Valor bruto estimado (sem desconto de IR/IOF)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(data.cdbs, key = PortfolioCdbPositionView::id) { cdb ->
                        CdbPositionCard(cdb) {
                            deleteTarget = PortfolioDeleteTarget.Cdb(
                                id = cdb.id,
                                label = "o CDB de ${cdb.principal.toBrl()}",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStockDialog) {
        RegisterStockDialog(
            gateway = gateway,
            onDismiss = { showStockDialog = false },
            onSaved = {
                showStockDialog = false
                onNotify("Lote de ação cadastrado.")
                reload()
            },
        )
    }

    if (showCdbDialog) {
        RegisterCdbDialog(
            gateway = gateway,
            onDismiss = { showCdbDialog = false },
            onSaved = {
                showCdbDialog = false
                onNotify("CDB cadastrado.")
                reload()
            },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Excluir posição",
            message = "Tem certeza que deseja excluir ${target.label}? Esta ação não pode ser desfeita.",
            confirmLabel = "Excluir",
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    runCatching {
                        when (target) {
                            is PortfolioDeleteTarget.StockLot -> gateway.deleteStockLot(target.id)
                            is PortfolioDeleteTarget.Cdb -> gateway.deleteCdb(target.id)
                        }
                    }
                        .onSuccess {
                            onNotify("Posição excluída.")
                            reload()
                        }
                        .onFailure { error = describeFailure(it, "Não foi possível excluir a posição.") }
                }
            },
        )
    }
}

@Composable
private fun PortfolioTotalsCard(summary: PortfolioSummaryView) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalRow("Custo total", summary.totalCost.toBrl())
            TotalRow(if (summary.complete) "Valor atual" else "Valor atual (parcial)", summary.totalCurrent.toBrl())
            TotalRow("Resultado (P&L)", summary.totalPnl.toBrl())
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StockPositionCard(
    stock: PortfolioStockPositionView,
    onDeleteLot: (PortfolioStockLotDetailView) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stock.ticker, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${stock.quantity} ações · PM ${stock.averagePrice.toBrl()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TotalRow("Custo de aquisição", stock.acquisitionCost.toBrl())
            TotalRow("Valor atual", stock.currentValue?.toBrl() ?: "Sem cotação")
            TotalRow("Resultado", stock.pnl?.toBrl() ?: "Sem cotação")
            stock.lots.forEach { lot ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${lot.purchaseDate} · ${lot.quantity} × ${lot.unitPrice.toBrl()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onDeleteLot(lot) },
                        modifier = Modifier.height(TouchTarget),
                    ) { Text("Excluir lote") }
                }
            }
        }
    }
}

@Composable
private fun CdbPositionCard(
    cdb: PortfolioCdbPositionView,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("CDB prefixado", fontWeight = FontWeight.Bold)
                    Text(
                        "Estimado · bruto, sem IR/IOF",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDelete, modifier = Modifier.height(TouchTarget)) {
                    Text("Excluir CDB")
                }
            }
            TotalRow("Principal aplicado", cdb.principal.toBrl())
            TotalRow("Valor estimado", cdb.estimatedValue.toBrl())
            TotalRow("Resultado estimado", cdb.pnl.toBrl())
        }
    }
}

@Composable
private fun RegisterStockDialog(
    gateway: InvestmentsGateway,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var ticker by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }
    var unitPriceDigits by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    FormDialog(
        title = "Cadastrar ação",
        confirmLabel = "Salvar ação",
        busy = busy,
        message = message,
        onDismiss = onDismiss,
        onConfirm = {
            val qty = quantityText.trim().toLongOrNull()
            val price = AmountInput.toMoney(unitPriceDigits)
            when {
                ticker.isBlank() -> message = "Informe o ticker B3."
                qty == null || qty <= 0 -> message = "Informe uma quantidade inteira positiva."
                price.cents <= 0 -> message = "Informe um preço unitário maior que zero."
                purchaseDate.isBlank() -> message = "Informe a data de compra (AAAA-MM-DD)."
                else -> scope.launch {
                    busy = true
                    message = null
                    runCatching {
                        gateway.registerStockLot(
                            NewStockLot(
                                ticker = ticker.trim().uppercase(),
                                quantity = qty,
                                unitPrice = price,
                                purchaseDate = purchaseDate.trim(),
                            ),
                        )
                    }
                        .onSuccess { onSaved() }
                        .onFailure { message = describeFailure(it, "Não foi possível cadastrar o lote de ação.") }
                    busy = false
                }
            }
        },
    ) {
        OutlinedTextField(
            value = ticker,
            onValueChange = { ticker = it },
            label = { Text("Ticker B3") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = quantityText,
            onValueChange = { quantityText = it.filter(Char::isDigit) },
            label = { Text("Quantidade") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        AmountField(
            digits = unitPriceDigits,
            onDigitsChange = { unitPriceDigits = it },
            label = "Preço unitário",
            enabled = !busy,
        )
        OutlinedTextField(
            value = purchaseDate,
            onValueChange = { purchaseDate = it },
            label = { Text("Data de compra") },
            supportingText = { Text("Formato AAAA-MM-DD") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RegisterCdbDialog(
    gateway: InvestmentsGateway,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var principalDigits by remember { mutableStateOf("") }
    var annualRate by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var maturityDate by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    FormDialog(
        title = "Cadastrar CDB",
        confirmLabel = "Salvar CDB",
        busy = busy,
        message = message,
        onDismiss = onDismiss,
        onConfirm = {
            val principal = AmountInput.toMoney(principalDigits)
            val annualRateDecimal = parseAnnualRatePercentToDecimal(annualRate)
            when {
                principal.cents <= 0 -> message = "Informe um valor aplicado maior que zero."
                annualRateDecimal == null -> message = "Informe uma taxa anual válida em percentual (ex.: 12,50)."
                startDate.isBlank() || maturityDate.isBlank() -> message = "Informe as datas de início e vencimento."
                else -> scope.launch {
                    busy = true
                    message = null
                    runCatching {
                        gateway.registerCdb(
                            NewCdbPosition(
                                principal = principal,
                                annualRate = annualRateDecimal,
                                startDate = startDate.trim(),
                                maturityDate = maturityDate.trim(),
                            ),
                        )
                    }
                        .onSuccess { onSaved() }
                        .onFailure { message = describeFailure(it, "Não foi possível cadastrar o CDB.") }
                    busy = false
                }
            }
        },
    ) {
        AmountField(
            digits = principalDigits,
            onDigitsChange = { principalDigits = it },
            label = "Valor aplicado",
            enabled = !busy,
        )
        OutlinedTextField(
            value = annualRate,
            onValueChange = { annualRate = it },
            label = { Text("Taxa anual (% a.a.)") },
            supportingText = { Text("Ex.: 12,50") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it },
            label = { Text("Data de início") },
            supportingText = { Text("Formato AAAA-MM-DD") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = maturityDate,
            onValueChange = { maturityDate = it },
            label = { Text("Data de vencimento") },
            supportingText = { Text("Formato AAAA-MM-DD") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
