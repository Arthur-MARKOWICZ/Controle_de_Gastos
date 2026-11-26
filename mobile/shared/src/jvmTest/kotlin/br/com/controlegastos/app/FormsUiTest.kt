package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Validações dos diálogos de verba e de renda (tarefa 6.2). */
@OptIn(ExperimentalTestApi::class)
class FormsUiTest {

    private val allocationError = describeFailure(ApiException(422, ApiErrorCode.ALLOCATION_EXCEEDS_INCOME, ""), "x")
    private val forbiddenError = describeFailure(ApiException(403, ApiErrorCode.FORBIDDEN, ""), "x")
    private val incomeBelowError = describeFailure(ApiException(422, ApiErrorCode.INCOME_BELOW_BASE_ALLOCATIONS, ""), "x")

    @Test
    fun `cria verba de limite com o valor digitado em centavos`() = runComposeUiTest {
        val submitted = mutableListOf<NewEnvelope>()
        var dismissed = false
        setContent {
            VerbasTheme(darkTheme = false) {
                EnvelopeFormDialog(EnvelopePurpose.LIMIT, listOf(EnvelopePurpose.LIMIT), { envelope, done ->
                    submitted += envelope
                    done(null)
                }, { dismissed = true })
            }
        }

        onNodeWithText("Nome").performTextInput("  Mercado  ")
        onNodeWithText("Valor reservado por mês").performTextReplacement("1050")
        onNodeWithText("Criar verba").performClick()
        waitForIdle()

        val envelope = submitted.single()
        assertEquals("Mercado", envelope.name)
        assertEquals(Money.fromApiAmount("10.50"), envelope.baseAmount)
        assertEquals(null, envelope.annualAmount)
        assertTrue(dismissed)
    }

    @Test
    fun `sem nome o botao de criar fica desabilitado`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                EnvelopeFormDialog(EnvelopePurpose.LIMIT, listOf(EnvelopePurpose.LIMIT), { _, _ -> error("não deveria enviar") }, {})
            }
        }

        onNodeWithText("Valor reservado por mês").performTextReplacement("1000")

        onNodeWithText("Criar verba").assertIsNotEnabled()
    }

    @Test
    fun `sem valor o botao de criar fica desabilitado`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                EnvelopeFormDialog(EnvelopePurpose.LIMIT, listOf(EnvelopePurpose.LIMIT), { _, _ -> error("não deveria enviar") }, {})
            }
        }

        onNodeWithText("Nome").performTextInput("Mercado")

        onNodeWithText("Criar verba").assertIsNotEnabled()
    }

    @Test
    fun `gasto anual com valor zero nao pode ser criado`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                EnvelopeFormDialog(EnvelopePurpose.ANNUAL_EXPENSE, listOf(EnvelopePurpose.ANNUAL_EXPENSE), { _, _ -> error("não deveria enviar") }, {})
            }
        }

        onNodeWithText("Nome").performTextInput("IPVA")
        onNodeWithText("Valor anual").performTextReplacement("000")

        onNodeWithText("Criar verba").assertIsNotEnabled()
    }

    @Test
    fun `gasto anual com dia 30 em fevereiro nao pode ser criado`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                EnvelopeFormDialog(EnvelopePurpose.ANNUAL_EXPENSE, listOf(EnvelopePurpose.ANNUAL_EXPENSE), { _, _ -> error("não deveria enviar") }, {})
            }
        }

        onNodeWithText("Nome").performTextInput("IPVA")
        onNodeWithText("Valor anual").performTextReplacement("120000")
        onNodeWithText("Dia").performTextReplacement("30")
        onNodeWithText("Mês").performTextReplacement("2")
        onNodeWithText("Criar verba").assertIsNotEnabled()

        onNodeWithText("Dia").performTextReplacement("29")
        onNodeWithText("Criar verba").assertIsEnabled()
    }

    @Test
    fun `verba acima da renda mostra a mensagem e o dialogo continua aberto`() = runComposeUiTest {
        var dismissed = false
        setContent {
            VerbasTheme(darkTheme = false) {
                EnvelopeFormDialog(EnvelopePurpose.LIMIT, listOf(EnvelopePurpose.LIMIT), { _, done -> done(allocationError) }, { dismissed = true })
            }
        }

        onNodeWithText("Nome").performTextInput("Mercado")
        onNodeWithText("Valor reservado por mês").performTextReplacement("999999")
        onNodeWithText("Criar verba").performClick()
        waitForIdle()

        onNodeWithText("A soma das verbas passaria da sua renda do mês. Reduza o valor ou ajuste a renda.").assertExists()
        assertFalse(dismissed)
    }

    @Test
    fun `acao recusada por nao ser dono mostra a mensagem e libera nova tentativa`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                EnvelopeFormDialog(EnvelopePurpose.LIMIT, listOf(EnvelopePurpose.LIMIT), { _, done -> done(forbiddenError) }, {})
            }
        }

        onNodeWithText("Nome").performTextInput("Mercado")
        onNodeWithText("Valor reservado por mês").performTextReplacement("1000")
        onNodeWithText("Criar verba").performClick()
        waitForIdle()

        onNodeWithText("Só quem criou a verba pode fazer isso.").assertExists()
        onNodeWithText("Criar verba").assertIsEnabled()
    }

    @Test
    fun `salva a renda digitada em centavos e fecha`() = runComposeUiTest {
        val saved = mutableListOf<Money>()
        var dismissed = false
        setContent {
            VerbasTheme(darkTheme = false) {
                IncomeFormDialog(null, { amount, done -> saved += amount; done(null) }, { dismissed = true })
            }
        }

        onNodeWithText("Renda mensal").performTextReplacement("500000")
        onNodeWithText("Salvar renda").performClick()
        waitForIdle()

        assertEquals(listOf(Money.fromApiAmount("5000.00")), saved)
        assertTrue(dismissed)
    }

    @Test
    fun `renda vazia nao pode ser salva`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                IncomeFormDialog(null, { _, _ -> error("não deveria enviar") }, {})
            }
        }

        onNodeWithText("Salvar renda").assertIsNotEnabled()
    }

    @Test
    fun `renda zero vai como 0,00 e a recusa da API aparece sem fechar`() = runComposeUiTest {
        val saved = mutableListOf<Money>()
        var dismissed = false
        setContent {
            VerbasTheme(darkTheme = false) {
                IncomeFormDialog(null, { amount, done -> saved += amount; done(incomeBelowError) }, { dismissed = true })
            }
        }

        onNodeWithText("Renda mensal").performTextReplacement("0")
        onNodeWithText("Salvar renda").performClick()
        waitForIdle()

        assertEquals(listOf(Money.fromApiAmount("0.00")), saved)
        onNodeWithText("A renda informada é menor do que o total já reservado nas suas verbas.").assertExists()
        assertFalse(dismissed)
    }

    @Test
    fun `renda alterada em outro dispositivo mostra a mensagem de recarregar`() = runComposeUiTest {
        val concurrent = describeFailure(ApiException(409, ApiErrorCode.INCOME_CONCURRENT_CHANGE, ""), "x")
        setContent {
            VerbasTheme(darkTheme = false) {
                IncomeFormDialog(Money.fromApiAmount("3000.00"), { _, done -> done(concurrent) }, {})
            }
        }

        onNodeWithText("Salvar renda").performClick()
        waitForIdle()

        onNodeWithText("A renda mudou em outro dispositivo. Recarregue e tente de novo.").assertExists()
    }
}
