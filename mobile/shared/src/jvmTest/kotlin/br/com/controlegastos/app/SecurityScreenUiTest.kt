package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tela de segurança: métodos de login, senha e MFA (tarefa 6.3). */
@OptIn(ExperimentalTestApi::class)
class SecurityScreenUiTest {

    private val wrongPassword = ApiException(401, "PASSWORD_REQUIRED_INVALID", "Senha atual incorreta.")

    private class FakeAuth(
        var methods: LoginMethods = LoginMethods(hasPassword = true, linkedProviders = listOf(OAuthProvider.GOOGLE)),
        var mfa: MfaStatus = MfaStatus("DISABLED", null),
    ) : AuthGateway {
        var methodsFailure: Throwable? = null
        var mfaFailure: Throwable? = null
        var addPasswordFailure: Throwable? = null
        var unlinkFailure: Throwable? = null
        var disableFailure: Throwable? = null
        val added = mutableListOf<String>()
        val unlinked = mutableListOf<OAuthProvider>()
        val disabled = mutableListOf<String>()

        override suspend fun mfaStatus(): MfaStatus = mfaFailure?.let { throw it } ?: mfa
        override suspend fun loginMethods(): LoginMethods = methodsFailure?.let { throw it } ?: methods
        override suspend fun addPassword(password: String) {
            addPasswordFailure?.let { throw it }
            added += password
            methods = methods.copy(hasPassword = true)
        }
        override suspend fun unlinkProvider(provider: OAuthProvider) {
            unlinkFailure?.let { throw it }
            unlinked += provider
            methods = methods.copy(linkedProviders = methods.linkedProviders - provider)
        }
        override suspend fun disableMfa(password: String) {
            disableFailure?.let { throw it }
            disabled += password
        }
        override suspend fun regenerateRecoveryCodes(password: String): List<String> = throw IllegalStateException("n/a")
        override suspend fun startMfaEnrollment(password: String, restrictedToken: String?) = throw IllegalStateException("n/a")
        override suspend fun confirmMfaEnrollment(code: String, restrictedToken: String?) = throw IllegalStateException("n/a")
        override suspend fun restore(): AuthUser? = null
        override suspend fun login(email: String, password: String) = throw IllegalStateException("n/a")
        override suspend fun register(email: String, password: String) = Unit
        override suspend fun logout() = Unit
        override suspend fun verifyMfa(challengeId: String, code: String) = throw IllegalStateException("n/a")
        override suspend fun verifyRecoveryCode(challengeId: String, recoveryCode: String) = throw IllegalStateException("n/a")
        override suspend fun requestPasswordReset(email: String) = Unit
        override fun oauthStartUrl(provider: OAuthProvider) = "https://api.example.com/start"
        override suspend fun oauthConnectAuthorizationUrl(provider: OAuthProvider) = "https://accounts.example.com/auth"
        override suspend fun redeemOAuthHandoff(code: String) = throw IllegalStateException("n/a")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        gateway: FakeAuth,
        onLoggedOut: () -> Unit = {},
        onNotify: (String) -> Unit = {},
    ) {
        setContent {
            VerbasTheme(darkTheme = false) {
                SecurityScreen(gateway, qrImageContent = {}, onLoggedOut = onLoggedOut, onNotify = onNotify)
            }
        }
        waitForIdle()
    }

    @Test
    fun `cadastra senha para conta so com Google e avisa`() = runComposeUiTest {
        val gateway = FakeAuth(LoginMethods(hasPassword = false, linkedProviders = listOf(OAuthProvider.GOOGLE)))
        val notices = mutableListOf<String>()
        show(gateway, onNotify = { notices += it })

        onNodeWithText("Cadastrar uma senha").performClick()
        onNode(androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasText("Senha")).performTextInput("uma frase bem segura")
        onNodeWithText("Salvar senha").performClick()
        waitForIdle()

        assertEquals(listOf("uma frase bem segura"), gateway.added)
        assertEquals(listOf("Senha cadastrada."), notices)
        onNodeWithText("Configurada").assertExists()
    }

    @Test
    fun `senha curta nao pode ser enviada`() = runComposeUiTest {
        val gateway = FakeAuth(LoginMethods(hasPassword = false, linkedProviders = listOf(OAuthProvider.GOOGLE)))
        show(gateway)

        onNodeWithText("Cadastrar uma senha").performClick()
        onNode(androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasText("Senha")).performTextInput("onze chars!")

        onNodeWithText("Salvar senha").assertIsNotEnabled()
        assertTrue(gateway.added.isEmpty())
    }

    @Test
    fun `unico metodo de login nao oferece desconectar`() = runComposeUiTest {
        show(FakeAuth(LoginMethods(hasPassword = false, linkedProviders = listOf(OAuthProvider.GOOGLE))))

        onNodeWithText("Desconectar").assertDoesNotExist()
        onNodeWithText("Este é o seu único método de login. Cadastre outro antes de removê-lo.").assertExists()
    }

    @Test
    fun `senha atual errada ao desativar MFA nao encerra a sessao e diz que a senha esta errada`() = runComposeUiTest {
        val gateway = FakeAuth(mfa = MfaStatus("ENABLED", null)).apply { disableFailure = wrongPassword }
        var loggedOut = false
        show(gateway, onLoggedOut = { loggedOut = true })

        onNodeWithText("Gerenciar").performClick()
        onNodeWithText("Desativar MFA").performClick()
        onNodeWithText("Senha atual").performTextInput("senha errada de teste")
        onNodeWithText("Confirmar desativação").performClick()
        waitForIdle()

        assertFalse(loggedOut)
        onNodeWithText("Senha atual incorreta.").assertExists()
        onNodeWithText("Sua sessão expirou. Entre novamente.").assertDoesNotExist()
    }

    @Test
    fun `desativar MFA com a senha certa encerra a sessao`() = runComposeUiTest {
        val gateway = FakeAuth(mfa = MfaStatus("ENABLED", null))
        var loggedOut = false
        show(gateway, onLoggedOut = { loggedOut = true })

        onNodeWithText("Gerenciar").performClick()
        onNodeWithText("Desativar MFA").performClick()
        onNodeWithText("Senha atual").performTextInput("frase segura de teste")
        onNodeWithText("Confirmar desativação").performClick()
        waitForIdle()

        assertEquals(listOf("frase segura de teste"), gateway.disabled)
        assertTrue(loggedOut)
    }

    @Test
    fun `senha recusada pela API aparece no dialogo sem avisar sucesso`() = runComposeUiTest {
        val gateway = FakeAuth(LoginMethods(hasPassword = false, linkedProviders = listOf(OAuthProvider.GOOGLE))).apply {
            addPasswordFailure = ApiException(409, ApiErrorCode.PASSWORD_ALREADY_SET, "A conta já tem senha.")
        }
        val notices = mutableListOf<String>()
        show(gateway, onNotify = { notices += it })

        onNodeWithText("Cadastrar uma senha").performClick()
        onNode(androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasText("Senha")).performTextInput("uma frase bem segura")
        onNodeWithText("Salvar senha").performClick()
        waitForIdle()

        onNodeWithText("A conta já tem senha.").assertExists()
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `falha ao desconectar mostra a mensagem e o provedor continua conectado`() = runComposeUiTest {
        val gateway = FakeAuth(LoginMethods(hasPassword = true, linkedProviders = listOf(OAuthProvider.GOOGLE))).apply {
            unlinkFailure = ApiException(409, ApiErrorCode.LAST_LOGIN_METHOD, "Mantenha ao menos um método de login.")
        }
        show(gateway)

        onNodeWithText("Desconectar").performClick()
        onAllDesconectar()
        waitForIdle()

        onNodeWithText("Mantenha ao menos um método de login.").assertExists()
        assertTrue(gateway.unlinked.isEmpty())
    }

    @Test
    fun `falha ao consultar metodos de login mostra a mensagem exata`() = runComposeUiTest {
        show(FakeAuth().apply { methodsFailure = IllegalStateException("rede") })

        onNodeWithText("Não foi possível consultar seus métodos de login.").assertExists()
    }

    @Test
    fun `sessao expirada ao consultar o MFA esconde o cartao de MFA`() = runComposeUiTest {
        show(FakeAuth().apply { mfaFailure = ApiException(401, "", "") })

        onNodeWithText("Sua sessão expirou. Entre novamente.").assertExists()
        onNodeWithText("Verificação em duas etapas").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllDesconectar() {
        // O diálogo de confirmação reaproveita o rótulo "Desconectar": o último nó é o botão de confirmar.
        val nodes = onAllNodes(androidx.compose.ui.test.hasText("Desconectar") and androidx.compose.ui.test.hasClickAction())
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
    }
}
