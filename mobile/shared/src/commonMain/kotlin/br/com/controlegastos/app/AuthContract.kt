package br.com.controlegastos.app

data class AuthUser(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
)

interface AuthGateway {
    suspend fun restore(): AuthUser?
    suspend fun login(email: String, password: String): AuthUser
    suspend fun register(email: String, password: String)
    suspend fun logout()
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Anonymous : AuthState
    data object Expired : AuthState
    data class Authenticated(val user: AuthUser) : AuthState
}

class AuthSessionController(private val gateway: AuthGateway) {
    var state: AuthState = AuthState.Loading
        private set

    suspend fun restore(): AuthState {
        state = gateway.restore()?.let(AuthState::Authenticated) ?: AuthState.Anonymous
        return state
    }

    suspend fun login(email: String, password: String): AuthState {
        state = AuthState.Authenticated(gateway.login(email, password))
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
}

object UnavailableAuthGateway : AuthGateway {
    override suspend fun restore(): AuthUser? = null
    override suspend fun login(email: String, password: String): AuthUser =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun register(email: String, password: String) =
        error("Autenticação não configurada para esta plataforma")
    override suspend fun logout() = Unit
}
