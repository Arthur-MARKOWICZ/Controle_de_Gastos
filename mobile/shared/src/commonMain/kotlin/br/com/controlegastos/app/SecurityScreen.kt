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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface SecurityDialog {
    data object AddPassword : SecurityDialog
    data class Unlink(val provider: OAuthProvider) : SecurityDialog
}

/**
 * Segurança da conta: métodos de login e segundo fator.
 *
 * Conectar um provedor social ainda não é possível no aplicativo: a API entrega
 * a sessão do OAuth por cookie, redirecionando para a web. O fluxo nativo está
 * decidido no ADR-020 e depende do handoff que o backend ainda não expõe.
 */
@Composable
internal fun SecurityScreen(
    gateway: AuthGateway,
    qrImageContent: @Composable (String) -> Unit,
    onLoggedOut: () -> Unit,
    onNotify: (String) -> Unit,
) {
    var status by remember(gateway) { mutableStateOf<MfaStatus?>(null) }
    var methods by remember(gateway) { mutableStateOf<LoginMethods?>(null) }
    var dialog by remember { mutableStateOf<SecurityDialog?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMfa by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() = scope.launch {
        status = runCatching { gateway.mfaStatus() }.getOrElse { MfaStatus("DISABLED", null) }
        runCatching { gateway.loginMethods() }
            .onSuccess { methods = it }
            .onFailure { error = describeFailure(it, "Não foi possível consultar seus métodos de login.") }
    }

    LaunchedEffect(gateway) { reload() }

    // As telas de MFA já ocupam a tela inteira e rolam sozinhas; alternar para
    // elas evita aninhar duas listas roláveis, que não têm altura definida.
    if (showMfa) {
        if (status?.status == "ENABLED") {
            MfaAccountPanel(
                onDisable = { password, onResult ->
                    scope.launch {
                        runCatching { gateway.disableMfa(password) }
                            .onSuccess { onResult(null); onLoggedOut() }
                            .onFailure { onResult(describeFailure(it, "Senha incorreta ou operação indisponível.")) }
                    }
                },
                onRegenerateCodes = { password, onResult ->
                    scope.launch {
                        runCatching { gateway.regenerateRecoveryCodes(password) }
                            .onSuccess { onResult(it, null) }
                            .onFailure {
                                onResult(null, describeFailure(it, "Senha incorreta ou operação indisponível."))
                            }
                    }
                },
                onDone = { showMfa = false; reload() },
            )
        } else {
            MfaSettingsScreen(
                onStartEnrollment = { password, onResult ->
                    scope.launch {
                        runCatching { gateway.startMfaEnrollment(password, null) }
                            .onSuccess { onResult(it, null) }
                            .onFailure {
                                onResult(null, describeFailure(it, "Não foi possível iniciar a configuração. Confira a senha."))
                            }
                    }
                },
                onConfirmEnrollment = { code, onResult ->
                    scope.launch {
                        runCatching { gateway.confirmMfaEnrollment(code, null) }
                            .onSuccess { onResult(it, null) }
                            .onFailure {
                                onResult(null, "Código inválido ou expirado. Gere um novo QR Code e tente novamente.")
                            }
                    }
                },
                // Concluir o cadastro revoga todas as sessões, inclusive esta.
                onComplete = onLoggedOut,
                qrImageContent = qrImageContent,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionHeader("CONTA", "Segurança", "Como você entra e como protege o acesso.") }
        error?.let { item { ErrorCard(it) { reload() } } }
        item {
            when (val current = methods) {
                null -> LoadingBox("Carregando métodos de login", Modifier.fillMaxWidth())
                else -> LoginMethodsCard(
                    methods = current,
                    onAddPassword = { dialog = SecurityDialog.AddPassword },
                    onUnlink = { dialog = SecurityDialog.Unlink(it) },
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Verificação em duas etapas", fontWeight = FontWeight.Bold)
                    Text(
                        when (status?.status) {
                            "ENABLED" -> "Ativa. Você precisa de um código do aplicativo autenticador para entrar."
                            "PENDING" -> "Começada, mas ainda não confirmada."
                            null -> "Carregando…"
                            else -> "Desativada. Ative para exigir um código além da senha."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { showMfa = true },
                        enabled = status != null,
                        modifier = Modifier.fillMaxWidth().height(TouchTarget),
                    ) { Text(if (status?.status == "ENABLED") "Gerenciar" else "Ativar") }
                }
            }
        }
    }

    when (val open = dialog) {
        null -> Unit
        SecurityDialog.AddPassword -> PasswordDialog(
            title = "Cadastrar senha",
            description = "Com uma senha você também consegue entrar sem o provedor social.",
            confirmLabel = "Salvar senha",
            onSubmit = { password, result ->
                scope.launch {
                    runCatching { gateway.addPassword(password) }
                        .onSuccess { result(null); dialog = null; onNotify("Senha cadastrada."); reload() }
                        .onFailure { result(describeFailure(it, "Não foi possível cadastrar a senha.")) }
                }
            },
            onDismiss = { dialog = null },
        )
        is SecurityDialog.Unlink -> ConfirmDialog(
            title = "Desconectar ${open.provider.label}?",
            message = "Você deixa de entrar por ${open.provider.label}. Os outros métodos continuam valendo.",
            confirmLabel = "Desconectar",
            onConfirm = {
                dialog = null
                scope.launch {
                    runCatching { gateway.unlinkProvider(open.provider) }
                        .onSuccess { onNotify("${open.provider.label} desconectado."); reload() }
                        .onFailure { error = describeFailure(it, "Não foi possível desconectar.") }
                }
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun LoginMethodsCard(
    methods: LoginMethods,
    onAddPassword: () -> Unit,
    onUnlink: (OAuthProvider) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Como você entra", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Senha", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (methods.hasPassword) "Configurada" else "Não configurada", fontWeight = FontWeight.Bold)
            }
            OAuthProvider.entries.forEach { provider ->
                val linked = provider in methods.linkedProviders
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(provider.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (linked && !methods.isLastMethod) {
                        OutlinedButton(
                            onClick = { onUnlink(provider) },
                            modifier = Modifier.height(TouchTarget),
                        ) { Text("Desconectar") }
                    } else {
                        Text(if (linked) "Conectado" else "Não conectado", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!methods.hasPassword) {
                OutlinedButton(
                    onClick = onAddPassword,
                    modifier = Modifier.fillMaxWidth().height(TouchTarget),
                ) { Text("Cadastrar uma senha") }
            }
            if (methods.isLastMethod) {
                Text(
                    "Este é o seu único método de login. Cadastre outro antes de removê-lo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Conectar Google ou GitHub ainda é feito pela versão web.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun PasswordDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onSubmit: (String, FormResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    FormDialog(
        title = title,
        confirmLabel = confirmLabel,
        confirmEnabled = password.length in 12..128,
        busy = busy,
        message = message,
        onConfirm = {
            busy = true
            message = null
            onSubmit(password) { error ->
                busy = false
                message = error
            }
        },
        onDismiss = onDismiss,
    ) {
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Use de 12 a 128 caracteres.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
