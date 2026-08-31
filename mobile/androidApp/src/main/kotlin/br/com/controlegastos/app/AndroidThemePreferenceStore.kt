package br.com.controlegastos.app

import android.content.Context

class AndroidThemePreferenceStore(context: Context) : ThemePreferenceStore {
    private val preferences = context.getSharedPreferences("verbas_preferences", Context.MODE_PRIVATE)

    override fun read(): ThemeMode = runCatching {
        ThemeMode.valueOf(preferences.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    override fun write(mode: ThemeMode) {
        preferences.edit().putString("theme_mode", mode.name).apply()
    }
}
