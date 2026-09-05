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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun MfaLoginScreen(
    onVerify: (code: String, onResult: (String?) -> Unit) -> Unit,
    onUseRecoveryCode: (code: String, onResult: (String?) -> Unit) -> Unit,
) {
    var recoveryMode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Confirme sua identidade", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (recoveryMode) "Informe um dos seus códigos de recuperação."
                        else "Informe o código do seu aplicativo autenticador.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { recoveryMode = false; code = ""; message = null },
                            modifier = Modifier.weight(1f),
                        ) { Text("Código do app") }
                        TextButton(
                            onClick = { recoveryMode = true; code = ""; message = null },
                            modifier = Modifier.weight(1f),
                        ) { Text("Código de recuperação") }
                    }
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(if (recoveryMode) "Código de recuperação" else "Código de 6 dígitos") },
                        keyboardOptions = KeyboardOptions(keyboardType = if (recoveryMode) KeyboardType.Text else KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp).semantics { contentDescription = it }) }
                    Button(
                        enabled = !busy && code.isNotBlank(),
                        onClick = {
                            busy = true; message = null
                            val result: (String?) -> Unit = { error -> busy = false; message = error }
                            if (recoveryMode) onUseRecoveryCode(code, result) else onVerify(code, result)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                    ) { Text(if (busy) "Verificando…" else "Confirmar") }
                }
            }
        }
    }
}

@Composable
fun MfaAccountPanel(
    onDisable: (password: String, onResult: (String?) -> Unit) -> Unit,
    onRegenerateCodes: (password: String, onResult: (List<String>?, String?) -> Unit) -> Unit,
    onDone: () -> Unit,
) {
    var action by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var recoveryCodes by remember { mutableStateOf<List<String>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Autenticação em duas etapas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "A autenticação em duas etapas está ativa na sua conta.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )

                    when {
                        recoveryCodes != null -> Column {
                            Text(
                                "Novos códigos de recuperação. Guarde-os em local seguro; eles não serão mostrados novamente.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            recoveryCodes?.forEach { code -> Text(code, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { recoveryCodes = null },
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                            ) { Text("Concluído") }
                        }

                        action == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { action = "regenerate" }, modifier = Modifier.fillMaxWidth()) {
                                Text("Gerar novos recovery codes")
                            }
                            TextButton(onClick = { action = "disable" }, modifier = Modifier.fillMaxWidth()) {
                                Text("Desativar MFA")
                            }
                        }

                        else -> Column {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Senha atual") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                            Button(
                                enabled = !busy && password.isNotBlank(),
                                onClick = {
                                    busy = true; message = null
                                    if (action == "disable") {
                                        onDisable(password) { error ->
                                            busy = false; password = ""
                                            if (error == null) onDone() else message = error
                                        }
                                    } else {
                                        onRegenerateCodes(password) { codes, error ->
                                            busy = false; password = ""
                                            if (codes != null) { recoveryCodes = codes; action = null } else message = error
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                            ) { Text(if (busy) "Aguarde…" else if (action == "disable") "Confirmar desativação" else "Gerar novos códigos") }
                            TextButton(onClick = { action = null; message = null }, modifier = Modifier.fillMaxWidth()) {
                                Text("Cancelar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MfaSettingsScreen(
    onStartEnrollment: (password: String, onResult: (MfaEnrollmentStart?, String?) -> Unit) -> Unit,
    onConfirmEnrollment: (code: String, onResult: (List<String>?, String?) -> Unit) -> Unit,
    onComplete: () -> Unit,
    qrImageContent: @Composable (dataUri: String) -> Unit,
) {
    var enrollment by remember { mutableStateOf<MfaEnrollmentStart?>(null) }
    var recoveryCodes by remember { mutableStateOf<List<String>?>(null) }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Configurar autenticação em duas etapas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                    when {
                        recoveryCodes != null -> Column(Modifier.padding(top = 12.dp)) {
                            Text(
                                "Guarde estes códigos de recuperação em um local seguro. Cada um pode ser usado uma " +
                                    "única vez para entrar caso você perca acesso ao aplicativo autenticador. " +
                                    "Eles não serão mostrados novamente.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            recoveryCodes?.forEach { recoveryCode -> Text(recoveryCode, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = onComplete,
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                            ) { Text("Já guardei meus códigos, entrar novamente") }
                        }

                        enrollment != null -> {
                            val current = enrollment!!
                            Column(Modifier.padding(top = 12.dp)) {
                                Text(
                                    "Escaneie o QR Code com seu aplicativo autenticador e informe o primeiro código gerado.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                                qrImageContent(current.qrImageDataUri)
                                Text(
                                    "Não consegue escanear? Chave manual: ${current.manualEntryKey}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                                Text(
                                    "Expira em: ${current.pendingExpiresAt.take(16).replace('T', ' ')} UTC",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = code,
                                    onValueChange = { code = it },
                                    label = { Text("Código de 6 dígitos") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                                message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                                Button(
                                    enabled = !busy && code.isNotBlank(),
                                    onClick = {
                                        busy = true; message = null
                                        onConfirmEnrollment(code) { codes, error ->
                                            busy = false
                                            code = ""
                                            if (codes != null) recoveryCodes = codes else message = error
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                                ) { Text(if (busy) "Confirmando…" else "Ativar MFA") }
                            }
                        }

                        else -> Column(Modifier.padding(top = 12.dp)) {
                            Text(
                                "Confirme sua senha atual para começar.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Senha atual") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                            Button(
                                enabled = !busy && password.isNotBlank(),
                                onClick = {
                                    busy = true; message = null
                                    onStartEnrollment(password) { start, error ->
                                        busy = false
                                        password = ""
                                        if (start != null) enrollment = start else message = error
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                            ) { Text(if (busy) "Aguarde…" else "Continuar") }
                        }
                    }
                }
            }
        }
    }
}
