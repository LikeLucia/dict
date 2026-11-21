package kirakira.dokidoki.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Immutable
data class DictPalette(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val card2: Color,
    val tint: Color,
    val onTint: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val divider: Color,
    val popup: Color,
    val scrim: Color,
    val switchOn: Color,
    val switchOff: Color,
    val navFill: Color,
    val navStroke: Color,
    val dash: Color,
)

val LightPalette = DictPalette(
    isDark = false,
    bg = Color(0xFFF6F6F8),
    card = Color(0xFFFFFFFF),
    card2 = Color(0xFFF1F2F6),
    tint = Color(0xFFEAF2FF),
    onTint = Color(0xFF3482FF),
    accent = Color(0xFF3482FF),
    accent2 = Color(0xFF6FAAFF),
    onAccent = Color.White,
    text = Color(0xFF181A20),
    text2 = Color(0xFF8C93B0),
    text3 = Color(0xFFB5B7C2),
    divider = Color(0xFFEDEDF0),
    popup = Color(0xFFFFFFFF),
    scrim = Color(0x4D000000),
    switchOn = Color(0xFF3482FF),
    switchOff = Color(0xFFE4E5EA),
    navFill = Color(0xDBFFFFFF),
    navStroke = Color(0x66FFFFFF),
    dash = Color(0xFFC6CAD6),
)

val DarkPalette = DictPalette(
    isDark = true,
    bg = Color(0xFF161618),
    card = Color(0xFF232326),
    card2 = Color(0xFF2A2A2E),
    tint = Color(0xFF2B3B54),
    onTint = Color(0xFF5C9BFF),
    accent = Color(0xFF277AF7),
    accent2 = Color(0xFF4E8DFD),
    onAccent = Color.White,
    text = Color(0xFFF2F2F4),
    text2 = Color(0xFF8E93A8),
    text3 = Color(0xFF5F6273),
    divider = Color(0xFF2E2E33),
    popup = Color(0xFF2C2C31),
    scrim = Color(0x91000000),
    switchOn = Color(0xFF277AF7),
    switchOff = Color(0xFF3A3A40),
    navFill = Color(0xCC202024),
    navStroke = Color(0x596E6E78),
    dash = Color(0xFF46464E),
)

val LocalDictPalette = staticCompositionLocalOf { LightPalette }

object DictTheme {
    val palette: DictPalette
        @Composable get() = LocalDictPalette.current
}

@Composable
fun DictTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val p = if (darkTheme) DarkPalette else LightPalette
    val colors = remember(darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = p.accent,
                onPrimary = p.onAccent,
                primaryVariant = p.accent,
                background = p.bg,
                onBackground = p.text,
                onBackgroundVariant = p.text2,
                surface = p.bg,
                onSurface = p.text,
                surfaceContainer = p.card,
                onSurfaceContainer = p.text,
                surfaceContainerHigh = p.card2,
                onSurfaceContainerHigh = p.text2,
                surfaceContainerHighest = p.card2,
                tertiaryContainer = p.tint,
                onTertiaryContainer = p.onTint,
                dividerLine = p.divider,
                outline = p.divider,
                windowDimming = p.scrim,
            )
        } else {
            lightColorScheme(
                primary = p.accent,
                onPrimary = Color.White,
                primaryVariant = p.accent,
                background = p.bg,
                onBackground = p.text,
                onBackgroundVariant = p.text2,
                surface = p.bg,
                onSurface = p.text,
                surfaceContainer = p.card,
                onSurfaceContainer = p.text,
                surfaceContainerHigh = p.card2,
                onSurfaceContainerHigh = p.text2,
                surfaceContainerHighest = p.card2,
                tertiaryContainer = p.tint,
                onTertiaryContainer = p.onTint,
                dividerLine = p.divider,
                outline = p.divider,
                windowDimming = p.scrim,
            )
        }
    }
    MiuixTheme(colors = colors) {
        CompositionLocalProvider(LocalDictPalette provides p, content = content)
    }
}