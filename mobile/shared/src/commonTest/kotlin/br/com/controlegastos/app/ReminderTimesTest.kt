package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderTimesTest {

    @Test
    fun `offers 96 quarter-hour options`() {
        assertEquals(96, REMINDER_TIMES.size)
        assertEquals("00:00", REMINDER_TIMES.first())
        assertEquals("23:45", REMINDER_TIMES.last())
    }

    @Test
    fun `minutes until the next reminder later today`() {
        assertEquals(30, minutesUntilNextReminder(nowMinuteOfDay = 20 * 60, reminderTime = "20:30"))
    }

    @Test
    fun `reminder at or before now goes to tomorrow`() {
        assertEquals(24 * 60, minutesUntilNextReminder(nowMinuteOfDay = 20 * 60, reminderTime = "20:00"))
        assertEquals(24 * 60 - 60, minutesUntilNextReminder(nowMinuteOfDay = 21 * 60, reminderTime = "20:00"))
    }

    @Test
    fun `crosses midnight`() {
        assertEquals(30, minutesUntilNextReminder(nowMinuteOfDay = 23 * 60 + 45, reminderTime = "00:15"))
    }

    @Test
    fun `validates quarter-hour format`() {
        assertTrue(isReminderTime("06:45"))
        assertFalse(isReminderTime("06:40"))
        assertFalse(isReminderTime("6:45"))
        assertFalse(isReminderTime("24:00"))
    }

    @Test
    fun `nunca agenda para agora nem para o passado`() {
        for (now in 0 until 24 * 60 step 5) {
            for (time in REMINDER_TIMES) {
                assertTrue(minutesUntilNextReminder(now, time) in 1..24 * 60, "now=$now time=$time")
            }
        }
    }

    @Test
    fun `horario com espaco ou segundos nao e aceito`() {
        assertFalse(isReminderTime(" 06:45"))
        assertFalse(isReminderTime("06:45:00"))
        assertFalse(isReminderTime(""))
    }

    @Test
    fun `horario fora do passo de 15 minutos falha com a mensagem de validacao`() {
        val error = assertFailsWith<IllegalArgumentException> { minutesUntilNextReminder(0, "20:07") }
        assertEquals("Horário de lembrete inválido", error.message)
    }

    @Test
    fun `horario vazio falha com a mensagem de validacao`() {
        val error = assertFailsWith<IllegalArgumentException> { minutesUntilNextReminder(0, "") }
        assertEquals("Horário de lembrete inválido", error.message)
    }

    @Test
    fun `hora 25 falha com a mensagem de validacao`() {
        val error = assertFailsWith<IllegalArgumentException> { minutesUntilNextReminder(0, "25:00") }
        assertEquals("Horário de lembrete inválido", error.message)
    }
}
