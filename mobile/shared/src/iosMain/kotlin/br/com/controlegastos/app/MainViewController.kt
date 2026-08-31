package br.com.controlegastos.app

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { VerbasApp(themePreferenceStore = AppleThemePreferenceStore()) }
