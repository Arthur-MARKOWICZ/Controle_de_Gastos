package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.coroutines.startCoroutine

class AuthSessionControllerTest {
    @Test
    fun `restores an existing session without exposing a refresh token`() = runSuspend {
        val gateway = FakeAuthGateway(restored = AuthUser("user-1", "pessoa@example.com", false))
        val controller = AuthSessionController(gateway)

        assertEquals(AuthState.Authenticated(gateway.restored!!), controller.restore())
        assertEquals(1, gateway.restoreCalls)
    }

    @Test
    fun `logout becomes anonymous even when remote revocation fails`() = runSuspend {
        val gateway = FakeAuthGateway(logoutFailure = IllegalStateException("offline"))
        val controller = AuthSessionController(gateway)

        try {
            controller.logout()
        } catch (_: IllegalStateException) {
            // A falha remota não pode impedir a limpeza do estado local.
        }
        assertEquals(AuthState.Anonymous, controller.state)
    }

    private class FakeAuthGateway(
        val restored: AuthUser? = null,
        private val logoutFailure: Throwable? = null,
    ) : AuthGateway {
        var restoreCalls = 0
        override suspend fun restore(): AuthUser? { restoreCalls++; return restored }
        override suspend fun login(email: String, password: String) = error("not used")
        override suspend fun register(email: String, password: String) = Unit
        override suspend fun logout() { logoutFailure?.let { throw it } }
    }
}

private fun runSuspend(block: suspend () -> Unit) {
    var failure: Throwable? = null
    block.startCoroutine(object : kotlin.coroutines.Continuation<Unit> {
        override val context = kotlin.coroutines.EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { failure = result.exceptionOrNull() }
    })
    failure?.let { throw it }
}
