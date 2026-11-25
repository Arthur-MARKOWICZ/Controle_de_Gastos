package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
