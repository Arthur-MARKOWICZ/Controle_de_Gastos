package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Cartão do lembrete na tela Mais (spec ui-mobile/lembrete-diario). */
@OptIn(ExperimentalTestApi::class)
class DailyReminderCardUiTest {

    private class Notifications : NotificationGateway {
        var stored = NotificationPreferencesView(false, false, false, "20:00")
        override suspend fun loadNotificationPreferences() = stored
        override suspend fun saveNotificationPreferences(preferences: NotificationPreferencesView) =
            preferences.also { stored = it }
        override suspend fun hasExpenseToday() = false
    }

    private class Scheduler(private val granted: Boolean) : ReminderScheduler {
        val scheduled = mutableListOf<String>()
        override fun canNotify() = false
        override suspend fun requestPermission() = granted
        override fun schedule(time: String) { scheduled += time }
        override fun cancel() = Unit
    }

    @Test
    fun `permission denied keeps the choice and explains how to allow`() = runComposeUiTest {
        val gateway = Notifications()
        val scheduler = Scheduler(granted = false)
        val controller = ReminderSettingsController(gateway, scheduler)
        setContent {
            VerbasTheme(darkTheme = false) {
                MoreScreen("pessoa@example.com", ThemeMode.SYSTEM, {}, {}, controller, {})
            }
        }
        waitForIdle()

        onNodeWithText("Lembrete diário").performClick()
        waitForIdle()

        assertTrue(gateway.stored.dailyReminder)
        assertTrue(scheduler.scheduled.isEmpty())
        onNodeWithText("As notificações estão bloqueadas", substring = true).assertExists()
    }

    @Test
    fun `shows the saved time`() = runComposeUiTest {
        val gateway = Notifications().apply { stored = NotificationPreferencesView(true, false, false, "21:45") }
        val controller = ReminderSettingsController(gateway, Scheduler(granted = true))
        setContent {
            VerbasTheme(darkTheme = false) {
                MoreScreen("pessoa@example.com", ThemeMode.SYSTEM, {}, {}, controller, {})
            }
        }
        waitForIdle()

        onNodeWithText("Horário: 21:45").assertExists()
        assertEquals("21:45", controller.state.time)
    }

    private class ConfigurableNotifications(
        var stored: NotificationPreferencesView = NotificationPreferencesView(false, false, false, "20:00"),
        private val loadFailure: Throwable? = null,
        private val saveFailure: Throwable? = null,
    ) : NotificationGateway {
        var saves = 0
        override suspend fun loadNotificationPreferences() = loadFailure?.let { throw it } ?: stored
        override suspend fun saveNotificationPreferences(preferences: NotificationPreferencesView): NotificationPreferencesView {
            saves++
            saveFailure?.let { throw it }
            return preferences.also { stored = it }
        }
        override suspend fun hasExpenseToday() = false
    }

    private class AllowedScheduler : ReminderScheduler {
        val scheduled = mutableListOf<String>()
        var cancels = 0
        override fun canNotify() = true
        override suspend fun requestPermission() = true
        override fun schedule(time: String) { scheduled += time }
        override fun cancel() { cancels++ }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showMore(controller: ReminderSettingsController) {
        setContent {
            VerbasTheme(darkTheme = false) { MoreScreen("pessoa@example.com", ThemeMode.SYSTEM, {}, {}, controller, {}) }
        }
        waitForIdle()
    }

    @Test
    fun `ativar com permissao salva na API e agenda no horario escolhido`() = runComposeUiTest {
        val gateway = ConfigurableNotifications()
        val scheduler = AllowedScheduler()
        showMore(ReminderSettingsController(gateway, scheduler))

        onNodeWithText("Lembrete diário").performClick()
        waitForIdle()

        assertTrue(gateway.stored.dailyReminder)
        assertEquals(listOf("20:00"), scheduler.scheduled)
    }

    @Test
    fun `trocar o horario reagenda o lembrete ativo`() = runComposeUiTest {
        val gateway = ConfigurableNotifications(NotificationPreferencesView(true, false, false, "20:00"))
        val scheduler = AllowedScheduler()
        showMore(ReminderSettingsController(gateway, scheduler))

        onNodeWithText("Horário: 20:00").performClick()
        // O item fica fora da área visível do menu; a ação semântica não depende da posição.
        onNodeWithText("21:45").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitForIdle()

        assertEquals("21:45", gateway.stored.reminderTime)
        assertEquals("21:45", scheduler.scheduled.last())
        onNodeWithText("Horário: 21:45").assertExists()
    }

    @Test
    fun `desativar cancela o agendamento no aparelho`() = runComposeUiTest {
        val gateway = ConfigurableNotifications(NotificationPreferencesView(true, false, false, "20:00"))
        val scheduler = AllowedScheduler()
        showMore(ReminderSettingsController(gateway, scheduler))

        onNodeWithText("Lembrete diário").performClick()
        waitForIdle()

        assertFalse(gateway.stored.dailyReminder)
        assertTrue(scheduler.cancels > 0)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `falha ao carregar bloqueia o interruptor e nao agenda nada`() = runComposeUiTest {
        val gateway = ConfigurableNotifications(loadFailure = IllegalStateException("rede"))
        val scheduler = AllowedScheduler()
        showMore(ReminderSettingsController(gateway, scheduler))

        onNodeWithText("Não foi possível carregar o lembrete.").assertExists()
        onNodeWithText("Horário: 20:00").assertIsNotEnabled()
        assertEquals(0, gateway.saves)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `falha ao salvar mostra a mensagem e volta ao estado anterior sem agendar`() = runComposeUiTest {
        val gateway = ConfigurableNotifications(saveFailure = IllegalStateException("500"))
        val scheduler = AllowedScheduler()
        val controller = ReminderSettingsController(gateway, scheduler)
        showMore(controller)

        onNodeWithText("Lembrete diário").performClick()
        waitForIdle()

        onNodeWithText("Não foi possível salvar o lembrete. Tente de novo.").assertExists()
        assertFalse(controller.state.enabled)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `sessao expirada ao carregar pede para entrar de novo`() = runComposeUiTest {
        showMore(ReminderSettingsController(ConfigurableNotifications(loadFailure = ApiException(401, "", "")), AllowedScheduler()))

        onNodeWithText("Sua sessão expirou. Entre novamente.").assertExists()
    }
}
