package com.lifeadmin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lifeadmin.app.R

// ---- Fonts (both open source, SIL Open Font License; see /licenses) ----

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

val DmMono = FontFamily(
    Font(R.font.dmmono_regular, FontWeight.Normal),
    Font(R.font.dmmono_medium, FontWeight.Medium),
)

private val base = Typography()

private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Jakarta),
    displayMedium = base.displayMedium.copy(fontFamily = Jakarta),
    displaySmall = base.displaySmall.copy(fontFamily = Jakarta),
    headlineLarge = base.headlineLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Jakarta),
    bodyMedium = base.bodyMedium.copy(fontFamily = Jakarta),
    bodySmall = base.bodySmall.copy(fontFamily = Jakarta),
    labelLarge = base.labelLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Medium),
)

// ---- Colors ----

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F4F1),
    onPrimaryContainer = Color(0xFF0B4F49),
    secondary = Color(0xFF0E1726),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8EF),
    onSecondaryContainer = Color(0xFF0E1726),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7A1610),
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF0E1726),
    surface = Color(0xFFF4F6F8),
    onSurface = Color(0xFF0E1726),
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = Color(0xFF5B6878),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F3F6),
    surfaceContainerHighest = Color(0xFFE8ECF0),
    outline = Color(0xFFB8C2CC),
    outlineVariant = Color(0xFFDCE2E8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF04201D),
    primaryContainer = Color(0xFF12302C),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFE6EDF3),
    onSecondary = Color(0xFF0B1116),
    secondaryContainer = Color(0xFF1E2833),
    onSecondaryContainer = Color(0xFFE6EDF3),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF1C1300),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF3A1518),
    onErrorContainer = Color(0xFFFECACA),
    background = Color(0xFF0B1116),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0B1116),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1A232D),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF0E151B),
    surfaceContainerLow = Color(0xFF121A21),
    surfaceContainer = Color(0xFF141C24),
    surfaceContainerHigh = Color(0xFF1A232D),
    surfaceContainerHighest = Color(0xFF212C37),
    outline = Color(0xFF3A4654),
    outlineVariant = Color(0xFF26313D),
)

/** Extra colors the design uses that Material's scheme has no slot for. */
@Immutable
data class ExtraColors(
    val card: Color,
    val line: Color,
    val ink: Color,
    val onInk: Color,
    val muted: Color,
    val overdue: Color,
    val overdueBg: Color,
    val soon: Color,
    val soonDot: Color,
    val soonBg: Color,
    val later: Color,
    val laterBg: Color,
)

private val LightExtra = ExtraColors(
    card = Color.White,
    line = Color(0xFFDCE2E8),
    ink = Color(0xFF0E1726),
    onInk = Color.White,
    muted = Color(0xFF5B6878),
    overdue = Color(0xFFB42318),
    overdueBg = Color(0xFFFDECEC),
    soon = Color(0xFF92400E),
    soonDot = Color(0xFFB45309),
    soonBg = Color(0xFFFEF3E2),
    later = Color(0xFF0F766E),
    laterBg = Color(0xFFE6F4F1),
)

private val DarkExtra = ExtraColors(
    card = Color(0xFF141C24),
    line = Color(0xFF26313D),
    ink = Color(0xFFE6EDF3),
    onInk = Color(0xFF0B1116),
    muted = Color(0xFF94A3B8),
    overdue = Color(0xFFF87171),
    overdueBg = Color(0xFF3A1518),
    soon = Color(0xFFFBBF24),
    soonDot = Color(0xFFFBBF24),
    soonBg = Color(0xFF33270F),
    later = Color(0xFF5EEAD4),
    laterBg = Color(0xFF12302C),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

/** Shortcut: `Extra.colors.ink` etc. */
object Extra {
    val colors: ExtraColors
        @Composable get() = LocalExtraColors.current
}

@Composable
fun LifeAdminTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}
