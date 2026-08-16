package org.openui.contacts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val appColors = if (darkTheme) {
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

    CompositionLocalProvider(LocalAppColors provides appColors) {
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
}
