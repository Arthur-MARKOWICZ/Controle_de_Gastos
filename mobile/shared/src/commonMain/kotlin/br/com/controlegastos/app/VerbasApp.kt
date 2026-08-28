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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val VerbasColors = lightColorScheme(
    primary = Color(0xFF146C43), onPrimary = Color.White,
    background = Color(0xFFF4F5F1), onBackground = Color(0xFF17211B),
    surface = Color.White, onSurface = Color(0xFF17211B), outline = Color(0xFFD9DDD6),
)

private data class EnvelopePreview(val name: String, val purpose: String, val available: String, val progress: Float, val status: String)

private val previewEnvelopes = listOf(
    EnvelopePreview("Combustível", "Limite de gasto", "R$ 240,00", 0.60f, "Dentro do limite"),
    EnvelopePreview("Investimentos", "Meta de aporte", "R$ 1.200,00", 0.80f, "R$ 300,00 para a meta"),
    EnvelopePreview("Livros", "Saldo acumulado", "R$ 180,00", 0.45f, "Acumulou por 2 meses"),
)

@Composable
fun VerbasApp(authGateway: AuthGateway = UnavailableAuthGateway) {
    val controller = remember(authGateway) { AuthSessionController(authGateway) }
    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(controller) { authState = controller.restore() }

    MaterialTheme(colorScheme = VerbasColors) {
        when (val current = authState) {
            AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Restaurando sua sessão" })
            }
            AuthState.Anonymous, AuthState.Expired -> AuthScreen(
                expired = current == AuthState.Expired,
                onLogin = { email, password, onResult ->
                    scope.launch {
                        runCatching { controller.login(email, password) }
                            .onSuccess { authState = it; onResult(null) }
                            .onFailure { onResult("Não foi possível entrar com os dados informados.") }
                    }
                },
                onRegister = { email, password, onResult ->
                    scope.launch {
                        runCatching { controller.register(email, password) }
                            .onSuccess { onResult(null) }
                            .onFailure { onResult("Revise os dados e tente novamente.") }
                    }
                },
            )
            is AuthState.Authenticated -> Dashboard(current.user.email) {
                authState = AuthState.Anonymous
                scope.launch { runCatching { controller.logout() } }
            }
        }
    }
}

@Composable
private fun AuthScreen(
    expired: Boolean,
    onLogin: (String, String, (String?) -> Unit) -> Unit,
    onRegister: (String, String, (String?) -> Unit) -> Unit,
) {
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(if (expired) "Sua sessão expirou. Entre novamente." else null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        item {
            Text("VERBAS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text(if (registerMode) "Crie sua conta" else "Entre na sua conta", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (registerMode) "O e-mail ainda não será verificado e não há recuperação de senha."
                else "Seu access token fica apenas na memória do aplicativo.",
                color = Color(0xFF5E6862), modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { registerMode = false; message = null }, modifier = Modifier.weight(1f)) { Text("Entrar") }
                TextButton(onClick = { registerMode = true; message = null }, modifier = Modifier.weight(1f)) { Text("Criar conta") }
            }
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (registerMode) Text("Use de 12 a 128 caracteres; espaços e Unicode são aceitos.", color = Color(0xFF5E6862), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp).semantics { contentDescription = it }) }
            Button(
                enabled = !busy && email.isNotBlank() && password.length in 12..128,
                onClick = {
                    busy = true; message = null
                    val result: (String?) -> Unit = { error ->
                        busy = false
                        message = error ?: if (registerMode) "Cadastro recebido. Agora você pode entrar." else null
                        if (registerMode && error == null) { registerMode = false; password = "" }
                    }
                    if (registerMode) onRegister(email, password, result) else onLogin(email, password, result)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                shape = RoundedCornerShape(8.dp),
            ) { Text(if (busy) "Aguarde…" else if (registerMode) "Concluir cadastro" else "Entrar com segurança") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Dashboard(email: String, onLogout: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { MobileNavigation() }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.padding(top = 24.dp)) {
                    Text("AGOSTO DE 2026", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Olá", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(email, color = Color(0xFF5E6862), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onLogout) { Text("Sair da conta") }
                }
            }
            item { IncomeSummary() }
            item { Text("Suas verbas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(previewEnvelopes) { EnvelopeCard(it) }
            item { Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) { Text("Registrar gasto") } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun IncomeSummary() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Renda do mês", color = Color(0xFF5E6862), style = MaterialTheme.typography.labelLarge)
            Text("R$ 5.000,00", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { SummaryValue("Reservado", "R$ 4.250"); SummaryValue("Não alocado", "R$ 750") }
        }
    }
}

@Composable private fun SummaryValue(label: String, value: String) = Column { Text(label, color = Color(0xFF5E6862), style = MaterialTheme.typography.labelMedium); Text(value, fontWeight = FontWeight.SemiBold) }

@Composable
private fun EnvelopeCard(envelope: EnvelopePreview) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(envelope.name, fontWeight = FontWeight.SemiBold); Text(envelope.purpose, color = Color(0xFF5E6862), style = MaterialTheme.typography.bodySmall) }; Text(envelope.available, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { envelope.progress }, modifier = Modifier.fillMaxWidth().height(7.dp).semantics { contentDescription = "Progresso de ${envelope.name}: ${(envelope.progress * 100).toInt()}%" }, color = MaterialTheme.colorScheme.primary, trackColor = Color(0xFFEEF0EA))
            Spacer(Modifier.height(8.dp)); Text(envelope.status, color = Color(0xFF5E6862), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MobileNavigation() = Row(Modifier.fillMaxWidth().background(Color(0xFF18251E)).padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
    NavigationItem("Início", true); NavigationItem("Histórico", false); NavigationItem("Alertas", false)
}

@Composable
private fun NavigationItem(label: String, selected: Boolean) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(6.dp).background(if (selected) Color(0xFFD6F0DF) else Color.Transparent, CircleShape)); Spacer(Modifier.height(4.dp)); Text(label, color = if (selected) Color.White else Color(0xFFAEBCB3), style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
}
