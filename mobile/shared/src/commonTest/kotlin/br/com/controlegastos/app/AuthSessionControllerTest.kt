package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    @Test
    fun `login for an mfa-enabled account returns a challenge instead of a session`() = runSuspend {
        val gateway = FakeAuthGateway(mfaChallengeId = "desafio-1")
        val controller = AuthSessionController(gateway)

        val state = controller.login("pessoa@example.com", "senha")

        assertEquals(AuthState.MfaRequired("desafio-1"), state)
        assertEquals(AuthState.MfaRequired("desafio-1"), controller.state)
    }

    @Test
    fun `verifying the totp code after a challenge authenticates the user`() = runSuspend {
        val user = AuthUser("user-1", "pessoa@example.com", false)
        val gateway = FakeAuthGateway(mfaChallengeId = "desafio-1", verifiedUser = user)
        val controller = AuthSessionController(gateway)
        controller.login("pessoa@example.com", "senha")

        val state = controller.verifyMfa("123456")

        assertEquals(AuthState.Authenticated(user), state)
        assertEquals("desafio-1", gateway.lastVerifiedChallengeId)
        assertEquals("123456", gateway.lastVerifiedCode)
    }

    @Test
    fun `using a recovery code moves to the restricted recovery setup state`() = runSuspend {
        val gateway = FakeAuthGateway(mfaChallengeId = "desafio-1", restrictedToken = "token-restrito")
        val controller = AuthSessionController(gateway)
        controller.login("pessoa@example.com", "senha")

        val state = controller.verifyRecoveryCode("ABCDE-FGHJK")

        assertIs<AuthState.MfaRecoverySetup>(state)
        assertEquals("token-restrito", state.restrictedToken)
    }

    @Test
    fun `finishing the recovery setup never authenticates automatically`() = runSuspend {
        val gateway = FakeAuthGateway(mfaChallengeId = "desafio-1", restrictedToken = "token-restrito")
        val controller = AuthSessionController(gateway)
        controller.login("pessoa@example.com", "senha")
        controller.verifyRecoveryCode("ABCDE-FGHJK")

        val state = controller.finishMfaRecoverySetup()

        assertEquals(AuthState.Anonymous, state)
    }

    private class FakeAuthGateway(
        val restored: AuthUser? = null,
        private val logoutFailure: Throwable? = null,
        private val mfaChallengeId: String? = null,
        private val verifiedUser: AuthUser? = null,
        private val restrictedToken: String? = null,
    ) : AuthGateway {
        var restoreCalls = 0
        var lastVerifiedChallengeId: String? = null
        var lastVerifiedCode: String? = null

        override suspend fun restore(): AuthUser? { restoreCalls++; return restored }
        override suspend fun login(email: String, password: String): AuthUser {
            mfaChallengeId?.let { throw MfaRequiredException(it) }
            return error("not used")
        }
        override suspend fun register(email: String, password: String) = Unit
        override suspend fun logout() { logoutFailure?.let { throw it } }
        override suspend fun verifyMfa(challengeId: String, code: String): AuthUser {
            lastVerifiedChallengeId = challengeId
            lastVerifiedCode = code
            return verifiedUser ?: error("not used")
        }
        override suspend fun verifyRecoveryCode(challengeId: String, recoveryCode: String): String =
            restrictedToken ?: error("not used")
        override suspend fun startMfaEnrollment(password: String, restrictedToken: String?): MfaEnrollmentStart =
            error("not used")
        override suspend fun confirmMfaEnrollment(code: String, restrictedToken: String?): List<String> =
            error("not used")
        override suspend fun disableMfa(password: String) = error("not used")
        override suspend fun regenerateRecoveryCodes(password: String): List<String> = error("not used")
        override suspend fun mfaStatus(): MfaStatus = error("not used")
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
