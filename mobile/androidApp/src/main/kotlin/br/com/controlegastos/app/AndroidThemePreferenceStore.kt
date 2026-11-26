package br.com.controlegastos.app

import android.content.Context
import android.content.SharedPreferences

class AndroidThemePreferenceStore(private val preferences: SharedPreferences) : ThemePreferenceStore {

    constructor(context: Context) : this(context.getSharedPreferences("verbas_preferences", Context.MODE_PRIVATE))

    override fun read(): ThemeMode = runCatching {
        ThemeMode.valueOf(preferences.getString(KEY, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    override fun write(mode: ThemeMode) {
        preferences.edit().putString(KEY, mode.name).apply()
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}
