package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONException
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

    @Test
    fun `salvar envia so os quatro campos do contrato`() {
        val json = JSONObject(notificationPreferencesJson(NotificationPreferencesView(true, true, true, "20:00")))

        assertEquals(setOf("dailyReminder", "negativeBalance", "sharedExpense", "reminderTime"), json.keys().asSequence().toSet())
    }

    @Test
    fun `campos extras da API sao ignorados na leitura`() {
        val body = JSONObject(
            """{"dailyReminder": false, "negativeBalance": true, "sharedExpense": false, "reminderTime": "08:00",
                "lastReminderDate": "2026-11-24", "userId": "u-1"}""",
        )

        assertEquals(NotificationPreferencesView(false, true, false, "08:00"), readNotificationPreferences(body))
    }

    @Test
    fun `nao confunde o texto true com booleano em hasExpense`() {
        assertFailsWith<JSONException> { readHasExpense(JSONObject("""{"hasExpense": "talvez"}""")) }
    }

    @Test
    fun `preferencia sem horario falha com JSONException`() {
        assertFailsWith<JSONException> {
            readNotificationPreferences(JSONObject("""{"dailyReminder": true, "negativeBalance": false, "sharedExpense": false}"""))
        }
    }

    @Test
    fun `preferencia com tipo errado falha com JSONException`() {
        assertFailsWith<JSONException> {
            readNotificationPreferences(
                JSONObject("""{"dailyReminder": "sim", "negativeBalance": false, "sharedExpense": false, "reminderTime": "20:00"}"""),
            )
        }
    }

    @Test
    fun `resposta de hoje sem o campo falha com JSONException`() {
        assertFailsWith<JSONException> { readHasExpense(JSONObject("""{"date": "2026-11-24"}""")) }
    }
}
