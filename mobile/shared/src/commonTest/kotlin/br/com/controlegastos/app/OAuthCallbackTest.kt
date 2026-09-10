package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OAuthCallbackTest {
    @Test
    fun `reads the one time code the backend put on the app link`() {
        assertEquals(
            OAuthCallback.Code("abc123"),
            OAuthCallback.fromCallbackUri(mapOf("code" to "abc123")),
        )
    }

    @Test
    fun `carries the challenge when the account still needs a second factor`() {
        assertEquals(
            OAuthCallback.MfaRequired("desafio-1"),
            OAuthCallback.fromCallbackUri(mapOf("mfaRequired" to "true", "challengeId" to "desafio-1")),
        )
    }

    @Test
    fun `treats a reported error as a failure`() {
        assertEquals(OAuthCallback.Failed, OAuthCallback.fromCallbackUri(mapOf("error" to "oauth_failed")))
    }

    @Test
    fun `does not trust a challenge announced without an id`() {
        assertEquals(OAuthCallback.Failed, OAuthCallback.fromCallbackUri(mapOf("mfaRequired" to "true")))
        assertEquals(
            OAuthCallback.Failed,
            OAuthCallback.fromCallbackUri(mapOf("mfaRequired" to "true", "challengeId" to "")),
        )
    }

    @Test
    fun `ignores a link that carries nothing usable`() {
        assertNull(OAuthCallback.fromCallbackUri(emptyMap()))
        assertNull(OAuthCallback.fromCallbackUri(mapOf("code" to "")))
    }
}
