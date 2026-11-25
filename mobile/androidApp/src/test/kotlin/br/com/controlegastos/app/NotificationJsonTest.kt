package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class NotificationJsonTest {

    @Test
    fun `reads preferences from the api`() {
        val body = JSONObject(
            """{"dailyReminder": true, "negativeBalance": false, "sharedExpense": true, "reminderTime": "21:15"}""",
        )

        assertEquals(NotificationPreferencesView(true, false, true, "21:15"), readNotificationPreferences(body))
    }

    @Test
    fun `writes every field when saving`() {
        val json = JSONObject(notificationPreferencesJson(NotificationPreferencesView(false, true, false, "07:30")))

        assertFalse(json.getBoolean("dailyReminder"))
        assertTrue(json.getBoolean("negativeBalance"))
        assertFalse(json.getBoolean("sharedExpense"))
        assertEquals("07:30", json.getString("reminderTime"))
    }

    @Test
    fun `reads whether there is an expense today`() {
        assertTrue(readHasExpense(JSONObject("""{"date": "2026-11-24", "hasExpense": true}""")))
        assertFalse(readHasExpense(JSONObject("""{"date": "2026-11-24", "hasExpense": false}""")))
    }
}
