package br.com.controlegastos.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.controlegastos.shared.generated.resources.Res
import br.com.controlegastos.shared.generated.resources.dm_sans_latin
import br.com.controlegastos.shared.generated.resources.ibm_plex_mono_400
import br.com.controlegastos.shared.generated.resources.ibm_plex_mono_600
import br.com.controlegastos.shared.generated.resources.newsreader_latin
import org.jetbrains.compose.resources.Font

/** 1B Suíço Preciso + 2E Carimbo & Tinta (espelha `DESIGN.md` e `web/src/app/globals.css`). */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F3A73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9EEF8),
    onPrimaryContainer = Color(0xFF111315),
    secondary = Color(0xFFBD2D28),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCEAEA),
    onSecondaryContainer = Color(0xFF5A1512),
    tertiary = Color(0xFF1D6B52),
    onTertiary = Color.White,
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF111315),
    surface = Color.White,
    onSurface = Color(0xFF111315),
    surfaceVariant = Color(0xFFE9ECF1),
    onSurfaceVariant = Color(0xFF525965),
    outline = Color(0xFFB8C0CC),
    outlineVariant = Color(0xFFDDE1E7),
    error = Color(0xFFBD2D28),
    errorContainer = Color(0xFFFCEAEA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DA2F0),
    onPrimary = Color(0xFF0B1324),
    primaryContainer = Color(0xFF1A2540),
    onPrimaryContainer = Color(0xFFF1F3F5),
    secondary = Color(0xFFF26B66),
    onSecondary = Color(0xFF3B1210),
    secondaryContainer = Color(0xFF3B1D1D),
    onSecondaryContainer = Color(0xFFFFE5E4),
    tertiary = Color(0xFF5EC7A3),
    onTertiary = Color(0xFF0A241B),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFF1F3F5),
    surface = Color(0xFF16191E),
    onSurface = Color(0xFFF1F3F5),
    surfaceVariant = Color(0xFF232831),
    onSurfaceVariant = Color(0xFF9AA3B2),
    outline = Color(0xFF434D5E),
    outlineVariant = Color(0xFF262C36),
    error = Color(0xFFF26B66),
    errorContainer = Color(0xFF3B1D1D),
)

/** Raios contidos do sistema 1B Suíço Preciso. */
private val VerbasShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
)

/**
 * 3B Editorial Preciso (DESIGN.md §2.3): Newsreader / DM Sans / IBM Plex Mono.
 *
 * Newsreader e DM Sans são TTFs variáveis. Em Android API 26+ o eixo `wght` é
 * aplicado via `FontWeight` nos `Font()`; em API 24–25 o eixo é ignorado e o
 * desenho cai no peso default do arquivo (ainda assim a família embarcada
 * substitui Serif/SansSerif do sistema). IBM Plex Mono usa arquivos estáticos
 * 400/600, seguros em toda a faixa do minSdk.
 */
@Composable
private fun rememberVerbasTypography(): Typography {
    val newsreader = FontFamily(
        Font(Res.font.newsreader_latin, weight = FontWeight.SemiBold),
        Font(Res.font.newsreader_latin, weight = FontWeight.Bold),
    )
    val dmSans = FontFamily(
        Font(Res.font.dm_sans_latin, weight = FontWeight.Normal),
        Font(Res.font.dm_sans_latin, weight = FontWeight.Medium),
        Font(Res.font.dm_sans_latin, weight = FontWeight.SemiBold),
        Font(Res.font.dm_sans_latin, weight = FontWeight.Bold),
    )
    val ibmPlexMono = FontFamily(
        Font(Res.font.ibm_plex_mono_400, weight = FontWeight.Normal),
        Font(Res.font.ibm_plex_mono_600, weight = FontWeight.SemiBold),
    )
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = newsreader,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = newsreader,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.25).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = newsreader,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = dmSans,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontFeatureSettings = "tnum",
        ),
        bodyLarge = TextStyle(
            fontFamily = dmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontFeatureSettings = "tnum",
        ),
        bodyMedium = TextStyle(
            fontFamily = dmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontFeatureSettings = "tnum",
        ),
        bodySmall = TextStyle(
            fontFamily = dmSans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFeatureSettings = "tnum",
        ),
        labelMedium = TextStyle(
            fontFamily = ibmPlexMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.6.sp,
        ),
    )
}

/** Cores por natureza de verba (2E Carimbo & Tinta — espelham `--purpose-*` da web). */
object PurposeColors {
    val limitLight = Color(0xFF1F3A73)
    val goalLight = Color(0xFF1D6B52)
    val fixedLight = Color(0xFFBD2D28)
    val savingsLight = Color(0xFF5B3E7A)
    val annualLight = Color(0xFF9A5B13)

    val limitDark = Color(0xFF7DA2F0)
    val goalDark = Color(0xFF5EC7A3)
    val fixedDark = Color(0xFFF26B66)
    val savingsDark = Color(0xFFB592DB)
    val annualDark = Color(0xFFE6A65C)
}

@Composable
fun purposeTone(purpose: String): Color {
    val dark = MaterialTheme.colorScheme.background.red < 0.2f &&
        MaterialTheme.colorScheme.background.green < 0.2f &&
        MaterialTheme.colorScheme.background.blue < 0.2f
    return when (purpose) {
        EnvelopePurpose.GOAL.api -> if (dark) PurposeColors.goalDark else PurposeColors.goalLight
        EnvelopePurpose.FIXED.api -> if (dark) PurposeColors.fixedDark else PurposeColors.fixedLight
        EnvelopePurpose.SAVINGS_TARGET.api -> if (dark) PurposeColors.savingsDark else PurposeColors.savingsLight
        EnvelopePurpose.ANNUAL_EXPENSE.api -> if (dark) PurposeColors.annualDark else PurposeColors.annualLight
        else -> if (dark) PurposeColors.limitDark else PurposeColors.limitLight
    }
}

@Composable
fun VerbasTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = VerbasShapes,
        typography = rememberVerbasTypography(),
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
                leadingIcon = {
                    if (option == mode) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                    } else {
                        RadioButton(
                            selected = false,
                            onClick = null,
                        )
                    }
                },
            )
        }
    }
}
