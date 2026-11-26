package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertFailsWith
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

    @Test
    fun `tema claro ignora o sistema escuro`() {
        val controller = ThemePreferenceController(FakeThemePreferenceStore(ThemeMode.LIGHT))

        assertEquals(false, controller.isDark(systemDark = true))
    }

    @Test
    fun `tema escuro ignora o sistema claro`() {
        val controller = ThemePreferenceController(FakeThemePreferenceStore(ThemeMode.DARK))

        assertEquals(true, controller.isDark(systemDark = false))
    }

    @Test
    fun `voltar para o sistema passa a seguir o sistema de novo`() {
        val store = FakeThemePreferenceStore(ThemeMode.DARK)
        val controller = ThemePreferenceController(store)

        controller.select(ThemeMode.SYSTEM)

        assertEquals(ThemeMode.SYSTEM, store.saved)
        assertEquals(false, controller.isDark(systemDark = false))
    }

    @Test
    fun `falha ao ler a preferencia propaga o erro do armazenamento`() {
        val failing = object : ThemePreferenceStore {
            override fun read(): ThemeMode = throw IllegalStateException("preferências corrompidas")
            override fun write(mode: ThemeMode) = Unit
        }

        val error = assertFailsWith<IllegalStateException> { ThemePreferenceController(failing) }
        assertEquals("preferências corrompidas", error.message)
    }

    @Test
    fun `falha ao gravar a escolha propaga o erro do armazenamento`() {
        val failing = object : ThemePreferenceStore {
            override fun read(): ThemeMode = ThemeMode.SYSTEM
            override fun write(mode: ThemeMode) = throw IllegalStateException("disco cheio")
        }
        val controller = ThemePreferenceController(failing)

        val error = assertFailsWith<IllegalStateException> { controller.select(ThemeMode.DARK) }
        assertEquals("disco cheio", error.message)
    }

    private class FakeThemePreferenceStore(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePreferenceStore {
        var saved = initial
        override fun read(): ThemeMode = saved
        override fun write(mode: ThemeMode) { saved = mode }
    }
}
