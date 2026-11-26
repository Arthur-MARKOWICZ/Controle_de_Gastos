package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DescribeFailureTest {

    private val fallback = "Não foi possível concluir."

    @Test
    fun `alocacao acima da renda vira frase especifica`() {
        val failure = ApiException(422, ApiErrorCode.ALLOCATION_EXCEEDS_INCOME, "Excede em 150.00")

        assertEquals(
            "A soma das verbas passaria da sua renda do mês. Reduza o valor ou ajuste a renda.",
            describeFailure(failure, fallback),
        )
    }

    @Test
    fun `sessao expirada pede para entrar novamente`() {
        assertEquals("Sua sessão expirou. Entre novamente.", describeFailure(ApiException(401, "", ""), fallback))
    }

    @Test
    fun `acao reservada ao dono explica quem pode fazer`() {
        assertEquals(
            "Só quem criou a verba pode fazer isso.",
            describeFailure(ApiException(403, ApiErrorCode.FORBIDDEN, "Acesso negado à verba Mercado"), fallback),
        )
    }

    @Test
    fun `tentativas demais pede para esperar`() {
        assertEquals(
            "Tentativas demais. Espere um pouco e tente de novo.",
            describeFailure(ApiException(429, ApiErrorCode.TOO_MANY_ATTEMPTS, ""), fallback),
        )
    }

    @Test
    fun `falha que nao e da API nunca vaza a mensagem tecnica`() {
        val message = describeFailure(IllegalStateException("java.net.SocketTimeoutException: 10.0.2.2"), fallback)

        assertEquals(fallback, message)
    }

    @Test
    fun `codigo conhecido ignora o detalhe do servidor com valores de outra verba`() {
        val message = describeFailure(
            ApiException(422, ApiErrorCode.INCOME_BELOW_BASE_ALLOCATIONS, "Verba Casa de Ana soma 3000.00"),
            fallback,
        )

        assertFalse(message.contains("3000.00"))
        assertFalse(message.contains("Ana"))
        assertEquals("A renda informada é menor do que o total já reservado nas suas verbas.", message)
    }

    @Test
    fun `problem sem codigo conhecido e sem detalhe usa o fallback`() {
        assertEquals(fallback, describeFailure(ApiException(500, "INTERNAL", "   "), fallback))
    }

    @Test
    fun `problem sem codigo conhecido usa o detalhe do servidor`() {
        assertEquals("Data no futuro.", describeFailure(ApiException(400, "INVALID_REQUEST", "Data no futuro."), fallback))
    }

    @Test
    fun `so 401 conta como sessao expirada`() {
        assertTrue(ApiException(401, "", "x").isSessionExpired())
        assertFalse(ApiException(403, ApiErrorCode.FORBIDDEN, "x").isSessionExpired())
        assertFalse(IllegalStateException("401").isSessionExpired())
    }

    @Test
    fun `senha atual errada diz que a senha esta errada e nao que a sessao expirou`() {
        val failure = ApiException(401, ApiErrorCode.PASSWORD_REQUIRED_INVALID, "Senha atual incorreta.")

        assertEquals("Senha atual incorreta.", describeFailure(failure, fallback))
    }
}
