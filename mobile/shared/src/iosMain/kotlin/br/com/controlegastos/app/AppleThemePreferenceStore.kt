package br.com.controlegastos.app

import platform.Foundation.NSUserDefaults

class AppleThemePreferenceStore : ThemePreferenceStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): ThemeMode {
        val stored = defaults.stringForKey("verbas.themeMode") ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    override fun write(mode: ThemeMode) {
        defaults.setObject(mode.name, forKey = "verbas.themeMode")
    }
}
