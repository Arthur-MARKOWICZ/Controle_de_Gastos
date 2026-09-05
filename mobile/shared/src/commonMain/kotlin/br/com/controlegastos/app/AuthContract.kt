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
}
