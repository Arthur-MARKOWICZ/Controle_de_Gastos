package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tom da mensagem nas telas de acesso.
 *
 * Erro e confirmação saíam os dois na cor da marca: quem teve a senha
 * recusada via a recusa com a mesma ênfase de um sucesso.
 */
class AuthNoticeTest {
    @Test
    fun `a rejected credential is an error, never the brand tone`() {
        val notice = authNotice(error = "Não foi possível entrar com os dados informados.", registerMode = false)

        assertEquals(NoticeTone.ERROR, notice?.tone)
        assertEquals("Não foi possível entrar com os dados informados.", notice?.text)
    }

    @Test
    fun `an accepted registration confirms in a tone of its own`() {
        val notice = authNotice(error = null, registerMode = true)

        assertEquals(NoticeTone.SUCCESS, notice?.tone)
        assertEquals("Cadastro recebido. Agora você pode entrar.", notice?.text)
    }

    @Test
    fun `a successful login says nothing because the screen changes`() {
        assertNull(authNotice(error = null, registerMode = false))
    }

    @Test
    fun `an expired session is reported as an error`() {
        assertEquals(NoticeTone.ERROR, expiredSessionNotice.tone)
    }

    @Test
    fun `the failure of a registration stays an error, not a confirmation`() {
        val notice = authNotice(error = "Revise os dados e tente novamente.", registerMode = true)

        assertEquals(NoticeTone.ERROR, notice?.tone)
        assertEquals("Revise os dados e tente novamente.", notice?.text)
    }
}
