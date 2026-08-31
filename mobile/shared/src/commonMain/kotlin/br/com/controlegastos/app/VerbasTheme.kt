package br.com.controlegastos.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6B69),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EFEC),
    onPrimaryContainer = Color(0xFF10201F),
    secondary = Color(0xFFC9472D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE7E2),
    onSecondaryContainer = Color(0xFF5B1D12),
    tertiary = Color(0xFF315FA8),
    onTertiary = Color.White,
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF10201F),
    surface = Color.White,
    onSurface = Color(0xFF10201F),
    surfaceVariant = Color(0xFFE9F0EE),
    onSurfaceVariant = Color(0xFF536664),
    outline = Color(0xFF9FB1AE),
    error = Color(0xFFA52F25),
    errorContainer = Color(0xFFFBE7E5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF54C6C0),
    onPrimary = Color(0xFF062826),
    primaryContainer = Color(0xFF153B3D),
    onPrimaryContainer = Color(0xFFF2F7F6),
    secondary = Color(0xFFFF8A6F),
    onSecondary = Color(0xFF43150D),
    secondaryContainer = Color(0xFF432923),
    onSecondaryContainer = Color(0xFFFFE5DE),
    tertiary = Color(0xFF82ADFF),
    onTertiary = Color(0xFF102B55),
    background = Color(0xFF0B1416),
    onBackground = Color(0xFFF2F7F6),
    surface = Color(0xFF111D20),
    onSurface = Color(0xFFF2F7F6),
    surfaceVariant = Color(0xFF1C2D31),
    onSurfaceVariant = Color(0xFFAABBB8),
    outline = Color(0xFF52676B),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF432526),
)

private val VerbasShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
)

@Composable
fun VerbasTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = VerbasShapes,
        content = content,
    )
}

@Composable
fun rememberResolvedTheme(mode: ThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return remember(mode, systemDark) {
        when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }
}

@Composable
fun ThemeMenu(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (mode) {
        ThemeMode.SYSTEM -> "Sistema"
        ThemeMode.LIGHT -> "Claro"
        ThemeMode.DARK -> "Escuro"
    }
    TextButton(
        onClick = { expanded = true },
        modifier = androidx.compose.ui.Modifier.semantics { contentDescription = "Tema atual: $label" },
    ) { Text("Tema: $label") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ThemeMode.entries.forEach { option ->
            val optionLabel = when (option) {
                ThemeMode.SYSTEM -> "Sistema"
                ThemeMode.LIGHT -> "Claro"
                ThemeMode.DARK -> "Escuro"
            }
            DropdownMenuItem(
                text = { Text(optionLabel) },
                onClick = { onSelect(option); expanded = false },
                modifier = androidx.compose.ui.Modifier.semantics { selected = option == mode },
                leadingIcon = { Text(if (option == mode) "●" else "○") },
            )
        }
    }
}
