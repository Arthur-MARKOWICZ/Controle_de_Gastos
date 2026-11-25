package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderSettingsControllerTest {

    private class FakeNotifications(
        var stored: NotificationPreferencesView = NotificationPreferencesView(false, false, false, "20:00"),
        var failSave: Boolean = false,
    ) : NotificationGateway {
        val saved = mutableListOf<NotificationPreferencesView>()
        override suspend fun loadNotificationPreferences() = stored
        override suspend fun saveNotificationPreferences(preferences: NotificationPreferencesView): NotificationPreferencesView {
            if (failSave) error("offline")
            saved += preferences
            stored = preferences
            return preferences
        }
        override suspend fun hasExpenseToday() = false
    }

    private class FakeScheduler(var granted: Boolean = true, var allowed: Boolean = true) : ReminderScheduler {
        val scheduled = mutableListOf<String>()
        var cancelled = 0
        var permissionRequests = 0
        override fun canNotify() = allowed
        override suspend fun requestPermission(): Boolean {
            permissionRequests++
            allowed = granted
            return granted
        }
        override fun schedule(time: String) { scheduled += time }
        override fun cancel() { cancelled++ }
    }

    @Test
    fun `enabling asks for permission, saves and schedules`() = runSuspend {
        val gateway = FakeNotifications()
        val scheduler = FakeScheduler(allowed = false)
        val controller = ReminderSettingsController(gateway, scheduler)
        controller.load()

        controller.setEnabled(true)

        assertEquals(1, scheduler.permissionRequests)
        assertEquals(true, gateway.saved.single().dailyReminder)
        assertEquals(listOf("20:00"), scheduler.scheduled)
        assertTrue(controller.state.enabled)
        assertFalse(controller.state.permissionDenied)
    }

    @Test
    fun `denied permission keeps the choice in the api but schedules nothing`() = runSuspend {
        val gateway = FakeNotifications()
        val scheduler = FakeScheduler(granted = false, allowed = false)
        val controller = ReminderSettingsController(gateway, scheduler)
        controller.load()

        controller.setEnabled(true)

        assertEquals(true, gateway.saved.single().dailyReminder)
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(controller.state.permissionDenied)
    }

    @Test
    fun `changing the time saves and reschedules only when enabled`() = runSuspend {
        val gateway = FakeNotifications(NotificationPreferencesView(true, true, false, "20:00"))
        val scheduler = FakeScheduler()
        val controller = ReminderSettingsController(gateway, scheduler)
        controller.load()

        controller.setTime("20:30")

        assertEquals(NotificationPreferencesView(true, true, false, "20:30"), gateway.saved.single())
        assertEquals(listOf("20:30"), scheduler.scheduled)

        controller.setEnabled(false)
        controller.setTime("07:15")
        assertEquals(1, scheduler.scheduled.size)
        assertTrue(scheduler.cancelled >= 1)
    }

    @Test
    fun `failed save reverts and reports an error`() = runSuspend {
        val gateway = FakeNotifications(failSave = true)
        val controller = ReminderSettingsController(gateway, FakeScheduler())
        controller.load()

        controller.setEnabled(true)

        assertFalse(controller.state.enabled)
        assertNotNull(controller.state.error)
    }

    @Test
    fun `sync follows the api on app start`() = runSuspend {
        val scheduler = FakeScheduler()
        ReminderSettingsController(FakeNotifications(NotificationPreferencesView(true, false, false, "22:00")), scheduler).sync()
        assertEquals(listOf("22:00"), scheduler.scheduled)

        ReminderSettingsController(FakeNotifications(), scheduler).sync()
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun `sync without permission does not schedule nor prompt`() = runSuspend {
        val scheduler = FakeScheduler(allowed = false)
        ReminderSettingsController(FakeNotifications(NotificationPreferencesView(true, false, false, "22:00")), scheduler).sync()

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(0, scheduler.permissionRequests)
    }

    @Test
    fun `load failure is reported`() = runSuspend {
        val gateway = object : NotificationGateway {
            override suspend fun loadNotificationPreferences(): NotificationPreferencesView = error("offline")
            override suspend fun saveNotificationPreferences(preferences: NotificationPreferencesView) = preferences
            override suspend fun hasExpenseToday() = false
        }
        val controller = ReminderSettingsController(gateway, FakeScheduler())

        controller.load()

        assertNull(controller.state.preferences)
        assertNotNull(controller.state.error)
    }
}
