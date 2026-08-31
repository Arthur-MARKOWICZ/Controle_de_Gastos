package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferenceControllerTest {
    @Test
    fun `uses system preference when no explicit choice was saved`() {
        val controller = ThemePreferenceController(FakeThemePreferenceStore())

        assertEquals(ThemeMode.SYSTEM, controller.mode)
        assertEquals(true, controller.isDark(systemDark = true))
        assertEquals(false, controller.isDark(systemDark = false))
    }

    @Test
    fun `persists explicit theme choices`() {
        val store = FakeThemePreferenceStore(ThemeMode.LIGHT)
        val controller = ThemePreferenceController(store)

        controller.select(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, controller.mode)
        assertEquals(ThemeMode.DARK, store.saved)
        assertEquals(true, controller.isDark(systemDark = false))
    }

    private class FakeThemePreferenceStore(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePreferenceStore {
        var saved = initial
        override fun read(): ThemeMode = saved
        override fun write(mode: ThemeMode) { saved = mode }
    }
}
