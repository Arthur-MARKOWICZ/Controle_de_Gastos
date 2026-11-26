package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Participantes de verba e histórico de renda no app (tarefa 6.3). */
@OptIn(ExperimentalTestApi::class)
class SharingAndIncomeHistoryUiTest {

    private val casa = EnvelopeView("e-1", "Casa", EnvelopePurpose.LIMIT.api, Money(100_000), Money(100_000), false)
    private val ana = ParticipantView("u-1", "ana@example.com", "2026-11-01T00:00:00Z")
    private val bruno = ParticipantView("u-2", "bruno@example.com", "2026-11-05T00:00:00Z")

    private class FakeSharing(
        var participants: List<ParticipantView> = emptyList(),
        val listFailure: Throwable? = null,
        val inviteResult: () -> ParticipantView = { error("n/a") },
        val removeFailure: Throwable? = null,
    ) : SharingGateway {
        val invited = mutableListOf<String>()
        override suspend fun listParticipants(envelopeId: String) = listFailure?.let { throw it } ?: participants
        override suspend fun inviteParticipant(envelopeId: String, email: String): ParticipantView {
            invited += email
            return inviteResult()
        }
        override suspend fun removeParticipant(envelopeId: String, userId: String) {
            removeFailure?.let { throw it }
            participants = participants.filterNot { it.userId == userId }
        }
    }

    private class FakeIncome(private val pages: Map<Int, IncomeHistoryPageView>, private val failure: Throwable? = null) : IncomeGateway {
        val requested = mutableListOf<Int>()
        override suspend fun loadIncome(month: YearMonth?): IncomeView? = null
        override suspend fun saveIncome(amount: Money): IncomeView = error("n/a")
        override suspend fun loadIncomeHistory(page: Int): IncomeHistoryPageView {
            requested += page
            failure?.let { throw it }
            return pages.getValue(page)
        }
    }

    // ---------------------------------------------------------------- participantes

    @Test
    fun `convida pelo e-mail sem espacos e avisa`() = runComposeUiTest {
        val gateway = FakeSharing(inviteResult = { bruno })
        val notices = mutableListOf<String>()
        setContent { VerbasTheme(darkTheme = false) { ParticipantsDialog(casa, gateway, { notices += it }, {}) } }
        waitForIdle()

        onNode(hasSetTextAction()).performTextInput("  bruno@example.com ")
        onNodeWithText("Convidar").performClick()
        waitForIdle()

        assertEquals(listOf("bruno@example.com"), gateway.invited)
        onNodeWithText("bruno@example.com").assertExists()
        assertEquals(listOf("Participante convidado."), notices)
    }

    @Test
    fun `e-mail so com espacos nao habilita o convite`() = runComposeUiTest {
        val gateway = FakeSharing()
        setContent { VerbasTheme(darkTheme = false) { ParticipantsDialog(casa, gateway, {}, {}) } }
        waitForIdle()

        onNode(hasSetTextAction()).performTextInput("   ")

        onNodeWithText("Convidar").assertIsNotEnabled()
        assertTrue(gateway.invited.isEmpty())
    }

    @Test
    fun `convite repetido nao duplica a pessoa na lista`() = runComposeUiTest {
        val gateway = FakeSharing(participants = listOf(ana), inviteResult = { ana })
        setContent { VerbasTheme(darkTheme = false) { ParticipantsDialog(casa, gateway, {}, {}) } }
        waitForIdle()

        onNode(hasSetTextAction()).performTextInput("ana@example.com")
        onNodeWithText("Convidar").performClick()
        waitForIdle()

        // O campo volta vazio; um segundo "ana@example.com" só existiria se a lista duplicasse a pessoa.
        onNodeWithText("ana@example.com").assertExists()
    }

    @Test
    fun `falha ao remover mantem a pessoa e explica`() = runComposeUiTest {
        val gateway = FakeSharing(participants = listOf(ana), removeFailure = ApiException(403, ApiErrorCode.FORBIDDEN, ""))
        setContent { VerbasTheme(darkTheme = false) { ParticipantsDialog(casa, gateway, {}, {}) } }
        waitForIdle()

        onNodeWithText("Remover").performClick()
        waitForIdle()

        onNodeWithText("Só quem criou a verba pode fazer isso.").assertExists()
        onNodeWithText("ana@example.com").assertExists()
    }

    @Test
    fun `falha ao carregar participantes mostra a mensagem`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                ParticipantsDialog(casa, FakeSharing(listFailure = IllegalStateException("rede")), {}, {})
            }
        }
        waitForIdle()

        onNodeWithText("Não foi possível carregar os participantes.").assertExists()
    }

    @Test
    fun `e-mail sem conta verificada mostra o detalhe da API e nao avisa sucesso`() = runComposeUiTest {
        val notices = mutableListOf<String>()
        val gateway = FakeSharing(inviteResult = { throw ApiException(422, "PARTICIPANT_NOT_ELIGIBLE", "Convite não enviado.") })
        setContent { VerbasTheme(darkTheme = false) { ParticipantsDialog(casa, gateway, { notices += it }, {}) } }
        waitForIdle()

        onNode(hasSetTextAction()).performTextInput("ninguem@example.com")
        onNodeWithText("Convidar").performClick()
        waitForIdle()

        onNodeWithText("Convite não enviado.").assertExists()
        assertTrue(notices.isEmpty())
    }

    // ---------------------------------------------------------- histórico de renda

    private fun item(id: String, amount: String, from: String) =
        IncomeHistoryItemView(id, Money.fromApiAmount(amount), from, "$from-01T12:00:00Z")

    @Test
    fun `lista a renda e avanca para a proxima pagina`() = runComposeUiTest {
        val gateway = FakeIncome(mapOf(
            0 to IncomeHistoryPageView(listOf(item("1", "5000.00", "2026-11")), 0, hasNext = true),
            1 to IncomeHistoryPageView(listOf(item("2", "4500.00", "2026-06")), 1, hasNext = false),
        ))
        setContent { VerbasTheme(darkTheme = false) { IncomeHistoryScreen(gateway) } }
        waitForIdle()

        onNodeWithText("Vale a partir de 2026-11").assertExists()
        onNodeWithText("Próximos").performClick()
        waitForIdle()

        onNodeWithText("Vale a partir de 2026-06").assertExists()
        onNodeWithText("Próximos").assertIsNotEnabled()
        assertEquals(listOf(0, 1), gateway.requested)
    }

    @Test
    fun `primeira pagina nao volta para pagina negativa`() = runComposeUiTest {
        val gateway = FakeIncome(mapOf(0 to IncomeHistoryPageView(listOf(item("1", "0.00", "2026-11")), 0, hasNext = false)))
        setContent { VerbasTheme(darkTheme = false) { IncomeHistoryScreen(gateway) } }
        waitForIdle()

        onNodeWithText("Anteriores").assertIsNotEnabled()
        onNodeWithText(Money(0).toBrl()).assertExists()
    }

    @Test
    fun `sem renda registrada mostra o estado vazio`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                IncomeHistoryScreen(FakeIncome(mapOf(0 to IncomeHistoryPageView(emptyList(), 0, false))))
            }
        }
        waitForIdle()

        onNodeWithText("Nenhuma renda registrada até agora.").assertExists()
    }

    @Test
    fun `falha ao carregar o historico mostra a mensagem e tenta de novo`() = runComposeUiTest {
        val gateway = FakeIncome(emptyMap(), IllegalStateException("rede"))
        setContent { VerbasTheme(darkTheme = false) { IncomeHistoryScreen(gateway) } }
        waitForIdle()

        onNodeWithText("Não foi possível carregar o histórico de renda.").assertExists()
        onNodeWithText("Tentar novamente").performClick()
        waitForIdle()
        assertEquals(listOf(0, 0), gateway.requested)
    }

    @Test
    fun `sessao expirada no historico pede para entrar de novo`() = runComposeUiTest {
        setContent { VerbasTheme(darkTheme = false) { IncomeHistoryScreen(FakeIncome(emptyMap(), ApiException(401, "", ""))) } }
        waitForIdle()

        onNodeWithText("Sua sessão expirou. Entre novamente.").assertExists()
    }
}
