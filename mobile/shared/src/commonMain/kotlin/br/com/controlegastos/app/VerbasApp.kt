package br.com.controlegastos.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun VerbasApp(
    authGateway: AuthGateway = UnavailableAuthGateway,
    financeGateway: FinanceGateway = UnavailableFinanceGateway,
    themePreferenceStore: ThemePreferenceStore = VolatileThemePreferenceStore,
    onThemeResolved: (Boolean) -> Unit = {},
) {
    val authController = remember(authGateway) { AuthSessionController(authGateway) }
    val themeController = remember(themePreferenceStore) { ThemePreferenceController(themePreferenceStore) }
    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }
    var themeMode by remember { mutableStateOf(themeController.mode) }
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
            is AuthState.Authenticated -> DashboardScreen(
                email = current.user.email,
                financeGateway = financeGateway,
                themeMode = themeMode,
                onThemeSelected = ::selectTheme,
                onLogout = {
                    authState = AuthState.Anonymous
                    scope.launch { runCatching { authController.logout() } }
                },
            )
        }
    }
}
