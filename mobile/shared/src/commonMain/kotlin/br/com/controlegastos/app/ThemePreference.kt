package br.com.controlegastos.app

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

interface ThemePreferenceStore {
    fun read(): ThemeMode
    fun write(mode: ThemeMode)
}

class ThemePreferenceController(private val store: ThemePreferenceStore) {
    var mode: ThemeMode = store.read()
        private set

    fun select(next: ThemeMode) {
        mode = next
        store.write(next)
    }

    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

object VolatileThemePreferenceStore : ThemePreferenceStore {
    private var mode = ThemeMode.SYSTEM
    override fun read(): ThemeMode = mode
    override fun write(mode: ThemeMode) { this.mode = mode }
}
