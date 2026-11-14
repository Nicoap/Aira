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
import androidx.compose.runtime.SideEffect
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
    // Тема больше не «светлая / тёмная»: её задаёт пресет или свои цвета, а оттенок схемы
    // выводится из яркости фона. «Бумага» и «Сепия» автоматически получают светлую схему,
    // «Графит» и «Полночь» — тёмную, отдельный переключатель не нужен.
    val background =
        parseThemeColor(
            backgroundColorHex,
            when (mode) {
                DataStoreManager.THEME_MODE_LIGHT -> LightColors.background
                DataStoreManager.THEME_MODE_SYSTEM ->
                    if (isSystemInDarkTheme()) md_theme_dark_background else LightColors.background
                else -> md_theme_dark_background
            },
        )
    val darkTheme = background.luminance() < 0.5f
    val primary = parseThemeColor(primaryColorHex, wavoraPrimary)
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
                // Подсказки и снекбары рисуются на inverseSurface: раньше это оставалось
                // дефолтным цветом Material, поэтому они не следовали выбранной теме.
                inverseSurface = lerp(surface, textColor, 0.14f),
                inverseOnSurface = textColor,
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
                // Подсказки и снекбары рисуются на inverseSurface: раньше это оставалось
                // дефолтным цветом Material, поэтому они не следовали выбранной теме.
                inverseSurface = lerp(surface, textColor, 0.14f),
                inverseOnSurface = textColor,
                surfaceTint = primary,
                inversePrimary = primary,
            )
        }
    val typography = typo()
    // Нейтральная рампа интерфейса считается из темы и обновляется на каждой её смене:
    // панель плеера, очередь и все прочие потребители WavoraTone больше не зашиты в тёмные
    // константы и следуют выбранным цветам.
    val palette =
        remember(background, surface, textColor) {
            wavoraPalette(background = background, surface = surface, text = textColor)
        }
    SideEffect { WavoraTone.apply(palette) }
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
    val accent = MaterialTheme.colorScheme.primary

    return remember(accent) {
        // theme_logo_mask.png: white star (opaque), black face (opaque), transparent outside.
        // Result: logo painted 100% in the accent color; the face stays cut out.
        //   RGB = accent (constant)
        //   alpha = R + A - 1  -> white+opaque = 1, black(face) = 0, transparent = 0,
        //                          anti-aliased edges keep their soft alpha.
        // NOTE: Compose ColorMatrix translation column (5th) is in 0..255, NOT 0..1 -
        // the previous version used 0..1 offsets, which made the logo practically invisible.
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, accent.red * 255f,
                    0f, 0f, 0f, 0f, accent.green * 255f,
                    0f, 0f, 0f, 0f, accent.blue * 255f,
                    1f, 0f, 0f, 1f, -255f,
                ),
            ),
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