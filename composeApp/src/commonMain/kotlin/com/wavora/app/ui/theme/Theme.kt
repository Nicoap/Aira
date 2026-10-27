package com.wavora.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.isSystemInDarkTheme
import com.wavora.domain.manager.DataStoreManager
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.theme_logo_mask

val DarkColors =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        errorContainer = md_theme_dark_errorContainer,
        onError = md_theme_dark_onError,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inverseSurface = md_theme_dark_inverseSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        outlineVariant = md_theme_dark_outlineVariant,
        scrim = md_theme_dark_scrim,
    )

private val LightColors =
    lightColorScheme(
        primary = wavoraPrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEBDDFF),
        onPrimaryContainer = Color(0xFF2D1050),
        secondary = Color(0xFF006879),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFB2EBF2),
        onSecondaryContainer = Color(0xFF001F25),
        background = Color(0xFFF8F7FA),
        onBackground = Color(0xFF1B1B1F),
        surface = Color(0xFFF8F7FA),
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = Color(0xFFE8E1EC),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    mode: String = DataStoreManager.THEME_MODE_DARK,
    primaryColorHex: String = "#A259FF",
    backgroundColorHex: String = "#0B0B0F",
    surfaceColorHex: String = "#12121A",
    textColorHex: String = "auto",
    secondaryTextColorHex: String = "auto",
    content:
        @Composable()
        () -> Unit,
) {
    val darkTheme =
        when (mode) {
            DataStoreManager.THEME_MODE_LIGHT -> false
            DataStoreManager.THEME_MODE_SYSTEM -> isSystemInDarkTheme()
            else -> true
        }
    val primary = parseThemeColor(primaryColorHex, wavoraPrimary)
    val background =
        parseThemeColor(
            backgroundColorHex,
            if (darkTheme) md_theme_dark_background else LightColors.background,
        )
    val surface =
        parseThemeColor(
            surfaceColorHex,
            if (darkTheme) md_theme_dark_surface else LightColors.surface,
        )
    val textColor = parseOptionalThemeColor(textColorHex) ?: contentColorFor(background)
    val secondaryTextColor =
        parseOptionalThemeColor(secondaryTextColorHex)
            ?: textColor.copy(alpha = 0.72f)
    val colorScheme =
        if (darkTheme) {
            DarkColors.copy(
                primary = primary,
                onPrimary = contentColorFor(primary),
                primaryContainer = lerp(background, primary, 0.24f),
                onPrimaryContainer = contentColorFor(background),
                secondary = primary,
                onSecondary = contentColorFor(primary),
                secondaryContainer = lerp(background, primary, 0.18f),
                onSecondaryContainer = contentColorFor(background),
                tertiary = primary,
                onTertiary = contentColorFor(primary),
                tertiaryContainer = lerp(background, primary, 0.18f),
                onTertiaryContainer = contentColorFor(background),
                background = background,
                onBackground = textColor,
                surface = surface,
                onSurface = textColor,
                surfaceVariant = surface,
                onSurfaceVariant = secondaryTextColor,
                outline = contentColorFor(surface).copy(alpha = 0.4f),
                outlineVariant = contentColorFor(surface).copy(alpha = 0.2f),
                surfaceTint = primary,
                inversePrimary = primary,
            )
        } else {
            LightColors.copy(
                primary = primary,
                onPrimary = contentColorFor(primary),
                primaryContainer = lerp(background, primary, 0.16f),
                onPrimaryContainer = contentColorFor(background),
                secondary = primary,
                onSecondary = contentColorFor(primary),
                secondaryContainer = lerp(background, primary, 0.12f),
                onSecondaryContainer = contentColorFor(background),
                tertiary = primary,
                onTertiary = contentColorFor(primary),
                tertiaryContainer = lerp(background, primary, 0.12f),
                onTertiaryContainer = contentColorFor(background),
                background = background,
                onBackground = textColor,
                surface = surface,
                onSurface = textColor,
                surfaceVariant = surface,
                onSurfaceVariant = secondaryTextColor,
                outline = contentColorFor(surface).copy(alpha = 0.4f),
                outlineVariant = contentColorFor(surface).copy(alpha = 0.2f),
                surfaceTint = primary,
                inversePrimary = primary,
            )
        }
    val typography = typo()
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        content = {
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onSurface,
                LocalAppTypography provides typography,
            ) {
                content()
            }
        },
        typography = typography,
    )
}





@Composable
fun themedWavoraLogoColorFilter(): ColorFilter {
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

    println(
        "Wavora logo filter: " +
            "background=$background, " +
            "accent=$accent"
    )

    return remember(background, accent) {
        val r = 0.299f
        val g = 0.587f
        val b = 0.114f

        val dr = accent.red - background.red
        val dg = accent.green - background.green
        val db = accent.blue - background.blue

        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    dr * r, dr * g, dr * b, 0f, background.red,
                    dg * r, dg * g, dg * b, 0f, background.green,
                    db * r, db * g, db * b, 0f, background.blue,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}






@Composable
fun themedWavoraLogoPainter(): Painter {
    val painter = painterResource(Res.drawable.theme_logo_mask)
    val colorFilter = themedWavoraLogoColorFilter()
    return remember(painter, colorFilter) {
        ThemedWavoraLogoPainter(painter, colorFilter)
    }
}

private class ThemedWavoraLogoPainter(
    private val delegate: Painter,
    private val colorFilter: ColorFilter,
) : Painter() {
    override val intrinsicSize = delegate.intrinsicSize

    override fun DrawScope.onDraw() {
        with(delegate) {
            draw(
                size = size,
                alpha = 1f,
                colorFilter = this@ThemedWavoraLogoPainter.colorFilter,
            )
        }
    }
}

private fun parseThemeColor(value: String, fallback: Color): Color {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6) return fallback
    val rgb = hex.toLongOrNull(16) ?: return fallback
    return Color(0xFF000000L or rgb)
}

private fun parseOptionalThemeColor(value: String): Color? =
    if (value.equals("auto", ignoreCase = true)) {
        null
    } else {
        val hex = value.trim().removePrefix("#")
        if (hex.length != 6) {
            null
        } else {
            hex.toLongOrNull(16)?.let { Color(0xFF000000L or it) }
        }
    }

private fun contentColorFor(color: Color): Color =
    if (color.luminance() >= 0.179f) Color(0xFF1B1B1F) else Color.White

@Composable
fun themeAccentBrush(): Brush {
    val primary = MaterialTheme.colorScheme.primary
    val gradientEnd = lerp(primary, MaterialTheme.colorScheme.onSurface, 0.16f)
    return remember(primary, gradientEnd) {
        Brush.horizontalGradient(listOf(primary, gradientEnd))
    }
}