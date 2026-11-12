package br.com.controlegastos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Tom da mensagem nas telas de acesso: recusa e confirmação não se parecem. */
internal enum class NoticeTone { ERROR, SUCCESS }

internal data class AuthNotice(val text: String, val tone: NoticeTone)

internal val expiredSessionNotice = AuthNotice("Sua sessão expirou. Entre novamente.", NoticeTone.ERROR)

/**
 * Mensagem a exibir depois de uma tentativa de entrar ou de se cadastrar.
 *
 * Entrar com sucesso não diz nada: a tela some e o aplicativo abre.
 */
internal fun authNotice(error: String?, registerMode: Boolean): AuthNotice? = when {
    error != null -> AuthNotice(error, NoticeTone.ERROR)
    registerMode -> AuthNotice("Cadastro recebido. Agora você pode entrar.", NoticeTone.SUCCESS)
    else -> null
}

@Composable
fun AuthScreen(
    expired: Boolean,
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onLogin: (String, String, (String?) -> Unit) -> Unit,
    onRegister: (String, String, (String?) -> Unit) -> Unit,
    onRecoverPassword: (String, (String?) -> Unit) -> Unit,
    socialProviders: List<OAuthProvider> = emptyList(),
    onSocialLogin: (OAuthProvider) -> Unit = {},
) {
    var registerMode by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(if (expired) expiredSessionNotice else null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("VERBAS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text("dinheiro com destino", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                ThemeMenu(themeMode, onThemeSelected)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(if (registerMode) "Crie sua conta" else "Entre na sua conta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (registerMode) "O e-mail ainda não será verificado. Você pode redefinir a senha por e-mail se precisar."
                        else "Acesse rapidamente suas verbas e saldos do mês.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { registerMode = false; notice = null; email = ""; password = "" },
                            modifier = Modifier.weight(1f).semantics { selected = !registerMode },
                        ) { Text("Entrar") }
                        TextButton(
                            onClick = { registerMode = true; notice = null; email = ""; password = "" },
                            modifier = Modifier.weight(1f).semantics { selected = registerMode },
                        ) { Text("Criar conta") }
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentType = ContentType.EmailAddress
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Senha",
                        newPassword = registerMode,
                    )
                    if (registerMode) Text("Use de 12 a 128 caracteres; espaços e Unicode são aceitos.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    notice?.let { current ->
                        Text(
                            current.text,
                            color = if (current.tone == NoticeTone.ERROR) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 12.dp).semantics {
                                liveRegion = if (current.tone == NoticeTone.ERROR) LiveRegionMode.Assertive
                                else LiveRegionMode.Polite
                                contentDescription = current.text
                            },
                        )
                    }
                    Button(
                        enabled = !busy && email.isNotBlank() &&
                            (if (registerMode) password.length in 12..128 else password.isNotEmpty()),
                        onClick = {
                            busy = true; notice = null
                            val wasRegistering = registerMode
                            val result: (String?) -> Unit = { error ->
                                busy = false
                                notice = authNotice(error, wasRegistering)
                                if (wasRegistering && error == null) { registerMode = false; password = "" }
                            }
                            if (registerMode) onRegister(email, password, result) else onLogin(email, password, result)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                    ) { Text(if (busy) "Aguarde…" else if (registerMode) "Concluir cadastro" else "Entrar com segurança") }
                    if (!registerMode) {
                        TextButton(
                            onClick = { recovering = true; notice = null },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text("Esqueci minha senha") }
                    }
                    if (socialProviders.isNotEmpty()) {
                        Text(
                            "ou entre com",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        socialProviders.forEach { provider ->
                            androidx.compose.material3.OutlinedButton(
                                onClick = { onSocialLogin(provider) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                            ) { Text("Continuar com ${provider.label}") }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CONTROLE SEM FICÇÃO", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Limites e compromissos reiniciam a cada mês. Gasto acima do planejado vira alerta, não bloqueio.", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (recovering) {
        PasswordRecoveryDialog(
            initialEmail = email,
            onSubmit = onRecoverPassword,
            onDismiss = { recovering = false },
        )
    }
}

/**
 * Solicitação de redefinição de senha.
 *
 * O link chega por e-mail e abre a versão web: o backend aponta o endereço para
 * `PUBLIC_APP_URL` e o aplicativo ainda não registra um App Link para recebê-lo.
 */
@Composable
private fun PasswordRecoveryDialog(
    initialEmail: String,
    onSubmit: (String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }

    if (sent) {
        ConfirmDialog(
            title = "Verifique seu e-mail",
            message = "Se houver uma conta com esse endereço, enviamos um link para redefinir a senha. " +
                "Abra o link no navegador para escolher a nova senha.",
            confirmLabel = "Entendi",
            onConfirm = onDismiss,
            onDismiss = onDismiss,
        )
        return
    }

    FormDialog(
        title = "Redefinir senha",
        confirmLabel = "Enviar link",
        confirmEnabled = email.isNotBlank(),
        busy = busy,
        message = message,
        onConfirm = {
            busy = true
            message = null
            onSubmit(email.trim()) { error ->
                busy = false
                message = error
                if (error == null) sent = true
            }
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
