package br.com.controlegastos.app

data class AuthUser(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
)

data class MfaEnrollmentStart(
    val otpauthUri: String,
    val qrImageDataUri: String,
    val manualEntryKey: String,
    val pendingExpiresAt: String,
)

data class MfaStatus(
    val status: String,
    val pendingExpiresAt: String?,
)

enum class OAuthProvider(val api: String, val label: String) {
    GOOGLE("google", "Google"),
    GITHUB("github", "GitHub"),
    ;

    companion object {
        fun fromApiOrNull(api: String): OAuthProvider? =
            entries.firstOrNull { it.api.equals(api, ignoreCase = true) }
    }
}

/** Como esta conta consegue entrar hoje: senha, provedores vinculados ou ambos. */
data class LoginMethods(
    val hasPassword: Boolean,
    val linkedProviders: List<OAuthProvider>,
) {
    /** Remover o último método deixaria a conta inacessível; o backend recusa. */
    val isLastMethod: Boolean get() = linkedProviders.size + (if (hasPassword) 1 else 0) <= 1
}

interface AuthGateway {
    suspend fun restore(): AuthUser?
    suspend fun login(email: String, password: String): AuthUser
    suspend fun register(email: String, password: String)
    suspend fun logout()
    suspend fun verifyMfa(challengeId: String, code: String): AuthUser
    suspend fun verifyRecoveryCode(challengeId: String, recoveryCode: String): String
    suspend fun startMfaEnrollment(password: String, restrictedToken: String? = null): MfaEnrollmentStart
    suspend fun confirmMfaEnrollment(code: String, restrictedToken: String? = null): List<String>
    suspend fun disableMfa(password: String)
    suspend fun regenerateRecoveryCodes(password: String): List<String>
    suspend fun mfaStatus(): MfaStatus
    suspend fun requestPasswordReset(email: String)
    suspend fun loginMethods(): LoginMethods
    suspend fun addPassword(password: String)
    suspend fun unlinkProvider(provider: OAuthProvider)

    /**
     * URL que o navegador do sistema deve abrir para o login social.
     *
     * O aplicativo nunca fala direto com o provider nem guarda `client_secret`:
     * o backend monta a autorização e devolve a sessão por um código de uso
     * único no App Link. Ver ADR-020.
     *
     * Serve apenas para entrar. Vincular um provedor a uma conta existente
     * exige o token de acesso na requisição, que o navegador do sistema não
     * carrega, e por isso continua sendo feito na web.
     */
    fun oauthStartUrl(provider: OAuthProvider): String

    /** Troca o código recebido pelo App Link pela sessão. */
    suspend fun redeemOAuthHandoff(code: String): AuthUser
}

class MfaRequiredException(val challengeId: String) : RuntimeException("Segundo fator necessário")

sealed interface AuthState {
    data object Loading : AuthState
    data object Anonymous : AuthState
    data object Expired : AuthState
    data class Authenticated(val user: AuthUser) : AuthState
    data class MfaRequired(val challengeId: String) : AuthState
    data class MfaRecoverySetup(val restrictedToken: String) : AuthState
}

class AuthSessionController(private val gateway: AuthGateway) {
    var state: AuthState = AuthState.Loading
        private set

    suspend fun restore(): AuthState {
        state = gateway.restore()?.let(AuthState::Authenticated) ?: AuthState.Anonymous
        return state
    }

    suspend fun login(email: String, password: String): AuthState {
        state = try {
            AuthState.Authenticated(gateway.login(email, password))
        } catch (mfaRequired: MfaRequiredException) {
            AuthState.MfaRequired(mfaRequired.challengeId)
        }
        return state
    }

    suspend fun register(email: String, password: String) = gateway.register(email, password)

    suspend fun logout() {
        try {
            gateway.logout()
        } finally {
            state = AuthState.Anonymous
        }
    }

    suspend fun verifyMfa(code: String): AuthState {
        val challenge = state as? AuthState.MfaRequired ?: error("Nenhum desafio de MFA pendente")
        state = AuthState.Authenticated(gateway.verifyMfa(challenge.challengeId, code))
        return state
    }

    suspend fun verifyRecoveryCode(code: String): AuthState {
        val challenge = state as? AuthState.MfaRequired ?: error("Nenhum desafio de MFA pendente")
        state = AuthState.MfaRecoverySetup(gateway.verifyRecoveryCode(challenge.challengeId, code))
        return state
    }

    /** Conclui o login social depois que o sistema entrega o App Link. */
    suspend fun completeOAuthHandoff(code: String): AuthState {
        state = AuthState.Authenticated(gateway.redeemOAuthHandoff(code))
        return state
    }

    /** O provider autenticou, mas a conta exige segundo fator. */
    fun requireMfa(challengeId: String): AuthState {
        state = AuthState.MfaRequired(challengeId)
        return state
    }

    fun finishMfaRecoverySetup(): AuthState {
        state = AuthState.Anonymous
        return state
    }
}

object UnavailableAuthGateway : AuthGateway {
    override suspend fun restore(): AuthUser? = null
    override suspend fun login(email: String, password: String): AuthUser =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun register(email: String, password: String) =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun logout() = Unit
    override suspend fun verifyMfa(challengeId: String, code: String): AuthUser =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun verifyRecoveryCode(challengeId: String, recoveryCode: String): String =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun startMfaEnrollment(password: String, restrictedToken: String?): MfaEnrollmentStart =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun confirmMfaEnrollment(code: String, restrictedToken: String?): List<String> =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun disableMfa(password: String) =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun regenerateRecoveryCodes(password: String): List<String> =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun mfaStatus(): MfaStatus =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun requestPasswordReset(email: String) =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun loginMethods(): LoginMethods =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun addPassword(password: String) =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun unlinkProvider(provider: OAuthProvider) =
        error("Autenticação não configurada para esta plataforma")
    override fun oauthStartUrl(provider: OAuthProvider): String =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun redeemOAuthHandoff(code: String): AuthUser =
        error("Autenticação não configurada para esta plataforma")
}
