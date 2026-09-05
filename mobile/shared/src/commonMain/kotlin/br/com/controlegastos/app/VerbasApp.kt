package br.com.controlegastos.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

private const val GENERIC_MFA_ERROR = "Não foi possível concluir a autenticação. Tente novamente."

@Composable
fun VerbasApp(
    authGateway: AuthGateway = UnavailableAuthGateway,
    financeGateway: FinanceGateway = UnavailableFinanceGateway,
    themePreferenceStore: ThemePreferenceStore = VolatileThemePreferenceStore,
    onThemeResolved: (Boolean) -> Unit = {},
    qrImageContent: @Composable (dataUri: String) -> Unit = { uri -> Text(uri) },
) {
    val authController = remember(authGateway) { AuthSessionController(authGateway) }
    val themeController = remember(themePreferenceStore) { ThemePreferenceController(themePreferenceStore) }
    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }
    var themeMode by remember { mutableStateOf(themeController.mode) }
    var showSecuritySettings by remember { mutableStateOf(false) }
    val darkTheme = rememberResolvedTheme(themeMode)
    val scope = rememberCoroutineScope()

    LaunchedEffect(authController) { authState = authController.restore() }
    SideEffect { onThemeResolved(darkTheme) }

    fun selectTheme(next: ThemeMode) {
        themeController.select(next)
        themeMode = next
    }

    VerbasTheme(darkTheme) {
        when (val current = authState) {
            AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Restaurando sua sessão" })
            }
            AuthState.Anonymous, AuthState.Expired -> AuthScreen(
                expired = current == AuthState.Expired,
                themeMode = themeMode,
                onThemeSelected = ::selectTheme,
                onLogin = { email, password, onResult ->
                    scope.launch {
                        runCatching { authController.login(email, password) }
                            .onSuccess { authState = it; onResult(null) }
                            .onFailure { onResult("Não foi possível entrar com os dados informados.") }
                    }
                },
                onRegister = { email, password, onResult ->
                    scope.launch {
                        runCatching { authController.register(email, password) }
                            .onSuccess { onResult(null) }
                            .onFailure { onResult("Revise os dados e tente novamente.") }
                    }
                },
            )
            is AuthState.MfaRequired -> MfaLoginScreen(
                onVerify = { code, onResult ->
                    scope.launch {
                        runCatching { authController.verifyMfa(code) }
                            .onSuccess { authState = it; onResult(null) }
                            .onFailure { onResult(GENERIC_MFA_ERROR) }
                    }
                },
                onUseRecoveryCode = { code, onResult ->
                    scope.launch {
                        runCatching { authController.verifyRecoveryCode(code) }
                            .onSuccess { authState = it; onResult(null) }
                            .onFailure { onResult(GENERIC_MFA_ERROR) }
                    }
                },
            )
            is AuthState.MfaRecoverySetup -> MfaSettingsScreen(
                onStartEnrollment = { password, onResult ->
                    scope.launch {
                        runCatching { authGateway.startMfaEnrollment(password, current.restrictedToken) }
                            .onSuccess { onResult(it, null) }
                            .onFailure { onResult(null, "Não foi possível iniciar a configuração. Confira a senha e tente novamente.") }
                    }
                },
                onConfirmEnrollment = { code, onResult ->
                    scope.launch {
                        runCatching { authGateway.confirmMfaEnrollment(code, current.restrictedToken) }
                            .onSuccess { onResult(it, null) }
                            .onFailure { onResult(null, "Código inválido ou expirado. Gere um novo QR Code e tente novamente.") }
                    }
                },
                onComplete = { authState = authController.finishMfaRecoverySetup() },
                qrImageContent = qrImageContent,
            )
            is AuthState.Authenticated -> if (showSecuritySettings) {
                MfaAccountSettings(
                    authGateway = authGateway,
                    qrImageContent = qrImageContent,
                    onDone = { showSecuritySettings = false },
                    onLoggedOut = {
                        showSecuritySettings = false
                        authState = AuthState.Anonymous
                        scope.launch { runCatching { authController.logout() } }
                    },
                )
            } else {
                DashboardScreen(
                    email = current.user.email,
                    financeGateway = financeGateway,
                    themeMode = themeMode,
                    onThemeSelected = ::selectTheme,
                    onOpenSecuritySettings = { showSecuritySettings = true },
                    onLogout = {
                        authState = AuthState.Anonymous
                        scope.launch { runCatching { authController.logout() } }
                    },
                )
            }
        }
    }
}

@Composable
private fun MfaAccountSettings(
    authGateway: AuthGateway,
    qrImageContent: @Composable (String) -> Unit,
    onDone: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    var status by remember { mutableStateOf<MfaStatus?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(authGateway) {
        status = runCatching { authGateway.mfaStatus() }.getOrNull()
            ?: MfaStatus(status = "DISABLED", pendingExpiresAt = null)
    }

    when (status?.status) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = "Carregando" })
        }
        "ENABLED" -> MfaAccountPanel(
            onDisable = { password, onResult ->
                scope.launch {
                    runCatching { authGateway.disableMfa(password) }
                        .onSuccess { onResult(null); onLoggedOut() }
                        .onFailure { onResult("Senha incorreta ou operação indisponível. Tente novamente.") }
                }
            },
            onRegenerateCodes = { password, onResult ->
                scope.launch {
                    runCatching { authGateway.regenerateRecoveryCodes(password) }
                        .onSuccess { onResult(it, null) }
                        .onFailure { onResult(null, "Senha incorreta ou operação indisponível. Tente novamente.") }
                }
            },
            onDone = onDone,
        )
        else -> MfaSettingsScreen(
            onStartEnrollment = { password, onResult ->
                scope.launch {
                    runCatching { authGateway.startMfaEnrollment(password, null) }
                        .onSuccess { onResult(it, null) }
                        .onFailure { onResult(null, "Não foi possível iniciar a configuração. Confira a senha e tente novamente.") }
                }
            },
            onConfirmEnrollment = { code, onResult ->
                scope.launch {
                    runCatching { authGateway.confirmMfaEnrollment(code, null) }
                        .onSuccess { onResult(it, null) }
                        .onFailure { onResult(null, "Código inválido ou expirado. Gere um novo QR Code e tente novamente.") }
                }
            },
            onComplete = onLoggedOut,
            qrImageContent = qrImageContent,
        )
    }
}
