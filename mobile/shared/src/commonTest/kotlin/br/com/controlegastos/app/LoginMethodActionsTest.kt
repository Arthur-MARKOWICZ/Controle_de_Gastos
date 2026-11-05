package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginMethodActionsTest {
    @Test
    fun provedorNaoVinculadoPodeSerConectado() {
        val methods = LoginMethods(hasPassword = true, linkedProviders = emptyList())
        val actions = providerLoginActions(methods)

        assertTrue(actions.all { it.canConnect })
        assertTrue(actions.none { it.linked })
        assertTrue(actions.none { it.canUnlink })
    }

    @Test
    fun provedorVinculadoPodeSerDesconectadoQuandoHaOutroMetodo() {
        val methods = LoginMethods(
            hasPassword = true,
            linkedProviders = listOf(OAuthProvider.GOOGLE),
        )
        val actions = providerLoginActions(methods)
        val google = actions.single { it.provider == OAuthProvider.GOOGLE }
        val github = actions.single { it.provider == OAuthProvider.GITHUB }

        assertTrue(google.linked)
        assertTrue(google.canUnlink)
        assertFalse(google.canConnect)
        assertTrue(github.canConnect)
        assertFalse(github.linked)
    }

    @Test
    fun ultimoMetodoNaoPodeSerDesconectado() {
        val methods = LoginMethods(
            hasPassword = false,
            linkedProviders = listOf(OAuthProvider.GITHUB),
        )
        val github = providerLoginActions(methods).single { it.provider == OAuthProvider.GITHUB }

        assertTrue(github.linked)
        assertFalse(github.canUnlink)
        assertFalse(github.canConnect)
        assertEquals(OAuthProvider.GITHUB, github.provider)
    }
}
