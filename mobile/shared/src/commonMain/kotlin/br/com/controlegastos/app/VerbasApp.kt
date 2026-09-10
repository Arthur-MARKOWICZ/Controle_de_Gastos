package br.com.controlegastos.app

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val GENERIC_MFA_ERROR = "Não foi possível concluir a autenticação. Tente novamente."

/**
 * Raiz do aplicativo.
 *
 * Mês e data de referência entram pela plataforma, como os demais recursos que
 * dependem do dispositivo. Os padrões existem para o iOS, que hoje roda sem
 * rede ([VerbasGateways.Unavailable]) e nunca chega a usá-los.
 */
@Composable
fun VerbasApp(
    gateways: VerbasGateways = VerbasGateways.Unavailable,
    currentMonth: YearMonth = YearMonth(1970, 1),
    today: String = currentMonth.firstDay(),
    themePreferenceStore: ThemePreferenceStore = VolatileThemePreferenceStore,
    onThemeResolved: (Boolean) -> Unit = {},
    qrImageContent: @Composable (dataUri: String) -> Unit = { uri -> Text(uri) },
    onOpenReport: (DownloadedReport) -> Unit = {},
    /** Abre a URL no navegador do sistema. Vazio desabilita o login social. */
    onOpenInBrowser: ((String) -> Unit)? = null,
    /** Resultado do App Link do login social, entregue pelo sistema. */
    oauthCallback: OAuthCallback? = null,
    onOAuthCallbackHandled: () -> Unit = {},
) {
    val authController = remember(gateways) { AuthSessionController(gateways.auth) }
    val themeController = remember(themePreferenceStore) { ThemePreferenceController(themePreferenceStore) }
    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }
    var themeMode by remember { mutableStateOf(themeController.mode) }
    val darkTheme = rememberResolvedTheme(themeMode)
    val scope = rememberCoroutineScope()

    LaunchedEffect(authController) { authState = authController.restore() }
    SideEffect { onThemeResolved(darkTheme) }

    // O sistema entregou o App Link do login social: troca o código pela sessão
    // ou entra no segundo fator, e consome o evento para não repetir.
    LaunchedEffect(oauthCallback) {
        when (val callback = oauthCallback) {
            null -> Unit
            is OAuthCallback.Code -> {
                authState = runCatching { authController.completeOAuthHandoff(callback.code) }
                    .getOrElse { AuthState.Anonymous }
                onOAuthCallbackHandled()
            }
            is OAuthCallback.MfaRequired -> {
                authState = authController.requireMfa(callback.challengeId)
                onOAuthCallbackHandled()
            }
            OAuthCallback.Failed -> {
                authState = AuthState.Anonymous
                onOAuthCallbackHandled()
            }
        }
    }

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
                            .onFailure { onResult(describeFailure(it, "Não foi possível entrar com os dados informados.")) }
                    }
                },
                onRegister = { email, password, onResult ->
                    scope.launch {
                        runCatching { authController.register(email, password) }
                            .onSuccess { onResult(null) }
                            .onFailure { onResult(describeFailure(it, "Revise os dados e tente novamente.")) }
                    }
                },
                onRecoverPassword = { email, onResult ->
                    scope.launch {
                        runCatching { gateways.auth.requestPasswordReset(email) }
                            .onSuccess { onResult(null) }
                            .onFailure { onResult(describeFailure(it, "Não foi possível enviar o e-mail agora.")) }
                    }
                },
                socialProviders = if (onOpenInBrowser == null) emptyList() else OAuthProvider.entries,
                onSocialLogin = { provider ->
                    onOpenInBrowser?.invoke(gateways.auth.oauthStartUrl(provider))
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
                        runCatching { gateways.auth.startMfaEnrollment(password, current.restrictedToken) }
                            .onSuccess { onResult(it, null) }
                            .onFailure { onResult(null, "Não foi possível iniciar a configuração. Confira a senha e tente novamente.") }
                    }
                },
                onConfirmEnrollment = { code, onResult ->
                    scope.launch {
                        runCatching { gateways.auth.confirmMfaEnrollment(code, current.restrictedToken) }
                            .onSuccess { onResult(it, null) }
                            .onFailure { onResult(null, "Código inválido ou expirado. Gere um novo QR Code e tente novamente.") }
                    }
                },
                onComplete = { authState = authController.finishMfaRecoverySetup() },
                qrImageContent = qrImageContent,
            )
            is AuthState.Authenticated -> SignedInApp(
                user = current.user,
                gateways = gateways,
                currentMonth = currentMonth,
                today = today,
                themeMode = themeMode,
                onThemeSelected = ::selectTheme,
                qrImageContent = qrImageContent,
                onOpenReport = onOpenReport,
                onSignOut = {
                    authState = AuthState.Anonymous
                    scope.launch { runCatching { authController.logout() } }
                },
                // O refresh falhou: volta ao login explicando o motivo, em vez de
                // deixar as telas repetirem "sessão expirada" sem saída.
                onSessionExpired = { authState = AuthState.Expired },
            )
        }
    }
}

/**
 * Aplicativo autenticado: barra inferior para os destinos recorrentes e uma
 * pilha para os destinos alcançados a partir deles.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SignedInApp(
    user: AuthUser,
    gateways: VerbasGateways,
    currentMonth: YearMonth,
    today: String,
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    qrImageContent: @Composable (String) -> Unit,
    onOpenReport: (DownloadedReport) -> Unit,
    onSignOut: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var backStack by remember { mutableStateOf<BackStack>(listOf(Route.Overview)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val financeController = remember(gateways, currentMonth) {
        FinanceDashboardController(gateways.ledger, currentMonth, gateways.envelopes, gateways.income)
    }
    val historyController = remember(gateways, currentMonth) { HistoryController(gateways.ledger, currentMonth) }

    fun go(next: Route) {
        backStack = backStack.push(next)
    }

    fun goBack() {
        backStack = backStack.pop()
    }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    BackHandler(enabled = backStack.canGoBack) { goBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (backStack.canGoBack) {
                        TextButton(onClick = { goBack() }, modifier = Modifier.height(TouchTarget)) { Text("‹ Voltar") }
                    }
                },
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("Verbas", fontWeight = FontWeight.Black)
                        Text(
                            user.email,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TOP_LEVEL_ROUTES.forEach { destination ->
                    NavigationBarItem(
                        selected = backStack.topLevel == destination,
                        onClick = { backStack = backStack.selectTopLevel(destination) },
                        icon = { Text(iconOf(destination)) },
                        label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = backStack.current) {
                Route.Overview, Route.Envelopes, Route.Goals, Route.AnnualExpenses -> MonthWorkspaceScreen(
                    route = current,
                    controller = financeController,
                    today = today,
                    onOpenEntries = { go(Route.EnvelopeEntries(it.id, it.name)) },
                    onNotify = ::notify,
                    onSessionExpired = onSessionExpired,
                )
                Route.History -> HistoryScreen(
                    controller = historyController,
                    envelopeGateway = gateways.envelopes,
                    onSessionExpired = onSessionExpired,
                )
                Route.More -> MoreScreen(
                    email = user.email,
                    themeMode = themeMode,
                    onThemeSelected = onThemeSelected,
                    onOpen = ::go,
                    onLogout = onSignOut,
                )
                Route.Reports -> ReportsScreen(
                    gateway = gateways.reports,
                    currentMonth = currentMonth,
                    onOpenReport = onOpenReport,
                    onNotify = ::notify,
                )
                Route.IncomeHistory -> IncomeHistoryScreen(gateways.income)
                Route.Security -> SecurityScreen(
                    gateway = gateways.auth,
                    qrImageContent = qrImageContent,
                    onLoggedOut = onSignOut,
                    onNotify = ::notify,
                )
                is Route.EnvelopeEntries -> EnvelopeEntriesScreen(
                    envelopeId = current.envelopeId,
                    envelopeName = current.envelopeName,
                    gateway = gateways.ledger,
                    month = financeController.month,
                    isCurrentMonth = financeController.isCurrentMonth,
                )
            }
        }
    }
}

private fun iconOf(route: Route.TopLevel): String = when (route) {
    Route.Overview -> "◎"
    Route.Envelopes -> "▤"
    Route.Goals -> "◈"
    Route.History -> "≡"
    Route.More -> "⋯"
}
