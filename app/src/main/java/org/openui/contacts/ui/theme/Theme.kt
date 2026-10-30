package org.openui.contacts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.openui.contacts.data.AppThemeMode

data class AppCustomColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val cardElevated: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primaryBrand: Color,
    val dialerGreen: Color,
    val actionRed: Color,
    val isDark: Boolean
)

val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.OPEN_UI }

val LocalAppColors = staticCompositionLocalOf {
    AppCustomColors(
        background = DarkBackground,
        surface = DarkSurface,
        card = DarkCard,
        cardElevated = DarkCardElevated,
        divider = DarkDivider,
        textPrimary = DarkTextPrimary,
        textSecondary = DarkTextSecondary,
        textMuted = DarkTextMuted,
        primaryBrand = BrandPurple,
        dialerGreen = DarkDialerGreen,
        actionRed = DarkActionRed,
        isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = DarkCardElevated,
    onPrimaryContainer = BrandPurpleLight,
    secondary = BrandVioletGlow,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = DarkDivider
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = LightCardElevated,
    onPrimaryContainer = BrandPurpleDark,
    secondary = BrandVioletGlow,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider,
    outlineVariant = LightDivider
)

private val M3DarkColorScheme = darkColorScheme(
    primary = M3PrimaryDark,
    onPrimary = Color(0xFF003062),
    primaryContainer = M3PrimaryContainerDark,
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = M3SecondaryDark,
    onSecondary = Color(0xFF00344F),
    background = M3BackgroundDark,
    onBackground = M3TextPrimaryDark,
    surface = M3SurfaceDark,
    onSurface = M3TextPrimaryDark,
    surfaceVariant = M3CardElevatedDark,
    onSurfaceVariant = M3TextSecondaryDark,
    outline = M3DividerDark,
    outlineVariant = M3DividerDark
)

private val M3LightColorScheme = lightColorScheme(
    primary = M3PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = M3PrimaryContainerLight,
    onPrimaryContainer = Color(0xFF041E49),
    secondary = M3SecondaryLight,
    onSecondary = Color.White,
    background = M3BackgroundLight,
    onBackground = M3TextPrimaryLight,
    surface = M3SurfaceLight,
    onSurface = M3TextPrimaryLight,
    surfaceVariant = M3CardElevatedLight,
    onSurfaceVariant = M3TextSecondaryLight,
    outline = M3DividerLight,
    outlineVariant = M3DividerLight
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.OPEN_UI,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.MATERIAL_3_EXPRESSIVE -> if (darkTheme) M3DarkColorScheme else M3LightColorScheme
        AppThemeMode.OPEN_UI -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val appColors = when (themeMode) {
        AppThemeMode.MATERIAL_3_EXPRESSIVE -> {
            if (darkTheme) {
                AppCustomColors(
                    background = M3BackgroundDark,
                    surface = M3SurfaceDark,
                    card = M3CardDark,
                    cardElevated = M3CardElevatedDark,
                    divider = M3DividerDark,
                    textPrimary = M3TextPrimaryDark,
                    textSecondary = M3TextSecondaryDark,
                    textMuted = M3TextMutedDark,
                    primaryBrand = M3PrimaryDark,
                    dialerGreen = Color(0xFF34A853),
                    actionRed = Color(0xFFF28B82),
                    isDark = true
                )
            } else {
                AppCustomColors(
                    background = M3BackgroundLight,
                    surface = M3SurfaceLight,
                    card = M3CardLight,
                    cardElevated = M3CardElevatedLight,
                    divider = M3DividerLight,
                    textPrimary = M3TextPrimaryLight,
                    textSecondary = M3TextSecondaryLight,
                    textMuted = M3TextMutedLight,
                    primaryBrand = M3PrimaryLight,
                    dialerGreen = Color(0xFF1E8E3E),
                    actionRed = Color(0xFFD93025),
                    isDark = false
                )
            }
        }
        AppThemeMode.OPEN_UI -> {
            if (darkTheme) {
                AppCustomColors(
                    background = DarkBackground,
                    surface = DarkSurface,
                    card = DarkCard,
                    cardElevated = DarkCardElevated,
                    divider = DarkDivider,
                    textPrimary = DarkTextPrimary,
                    textSecondary = DarkTextSecondary,
                    textMuted = DarkTextMuted,
                    primaryBrand = BrandPurple,
                    dialerGreen = DarkDialerGreen,
                    actionRed = DarkActionRed,
                    isDark = true
                )
            } else {
                AppCustomColors(
                    background = LightBackground,
                    surface = LightSurface,
                    card = LightCard,
                    cardElevated = LightCardElevated,
                    divider = LightDivider,
                    textPrimary = LightTextPrimary,
                    textSecondary = LightTextSecondary,
                    textMuted = LightTextMuted,
                    primaryBrand = BrandPurple,
                    dialerGreen = LightDialerGreen,
                    actionRed = LightActionRed,
                    isDark = false
                )
            }
        }
    }

    CompositionLocalProvider(
        LocalAppThemeMode provides themeMode,
        LocalAppColors provides appColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

object AppTheme {
    val colors: AppCustomColors
        @Composable
        get() = LocalAppColors.current

    val mode: AppThemeMode
        @Composable
        get() = LocalAppThemeMode.current
}

