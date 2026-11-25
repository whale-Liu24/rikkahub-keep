package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.PresetTheme

val GrokCharcoalThemePreset by lazy {
    PresetTheme(
        id = "grok_charcoal",
        name = { Text(stringResource(R.string.theme_name_home_charcoal)) },
        standardLight = lightScheme,
        standardDark = darkScheme,
    )
}

private val darkScheme = darkColorScheme(
    primary = Color(0xFFC5BFB7),
    onPrimary = Color(0xFF292622),
    primaryContainer = Color(0xFF403C36),
    onPrimaryContainer = Color(0xFFF0EAE2),
    inversePrimary = Color(0xFF57514A),
    secondary = Color(0xFFC9C3BB),
    onSecondary = Color(0xFF302B26),
    secondaryContainer = Color(0xFF3D3833),
    onSecondaryContainer = Color(0xFFEAE4DC),
    tertiary = Color(0xFFD4C2AF),
    onTertiary = Color(0xFF352C23),
    tertiaryContainer = Color(0xFF493C31),
    onTertiaryContainer = Color(0xFFF0E1D1),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF0F0F4),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFF0F0F4),
    surfaceVariant = Color(0xFF302F33),
    onSurfaceVariant = Color(0xFF9E9EA8),
    surfaceTint = Color(0xFFC5BFB7),
    inverseSurface = Color(0xFFF0F0F4),
    inverseOnSurface = Color(0xFF302F33),
    outline = Color(0xFF79777C),
    outlineVariant = Color(0xFF47454A),
    surfaceDim = Color(0xFF0E0E10),
    surfaceBright = Color(0xFF29292D),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF18181B),
    surfaceContainer = Color(0xFF1B1B1E),
    surfaceContainerHigh = Color(0xFF202024),
    surfaceContainerHighest = Color(0xFF26262A),
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF57514A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAE2D8),
    onPrimaryContainer = Color(0xFF28231E),
    inversePrimary = Color(0xFFD7D2CB),
    secondary = Color(0xFF615950),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E0D7),
    onSecondaryContainer = Color(0xFF29241E),
    tertiary = Color(0xFF6B5847),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E0CE),
    onTertiaryContainer = Color(0xFF302217),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF252320),
    surface = Color(0xFFF8F6F3),
    onSurface = Color(0xFF252320),
    surfaceVariant = Color(0xFFE9E4DE),
    onSurfaceVariant = Color(0xFF59544F),
    surfaceTint = Color(0xFF57514A),
    inverseSurface = Color(0xFF302D29),
    inverseOnSurface = Color(0xFFF4EFEA),
    outline = Color(0xFF827A73),
    outlineVariant = Color(0xFFD1C9C2),
    surfaceDim = Color(0xFFDED9D4),
    surfaceBright = Color(0xFFFDFBF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F3F0),
    surfaceContainer = Color(0xFFF0ECE8),
    surfaceContainerHigh = Color(0xFFEAE6E1),
    surfaceContainerHighest = Color(0xFFE3DED9),
)
