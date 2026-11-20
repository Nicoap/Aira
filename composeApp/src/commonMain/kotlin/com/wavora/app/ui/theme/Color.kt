package com.wavora.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ===== Aira Brand Colors — source of truth =====
// Палитра взята с самой иконки: розовая звезда на тёмно-синем. Пурпур и неон-циан
// остались от Wavora и больше не используются ни как акцент, ни как дефолт темы.
val wavoraPrimary = Color(0xFFFDC5E2)       // Aira Pink — main accent
val wavoraSecondary = Color(0xFFFF8FBF)     // Deeper Pink — active/secondary state
val wavoraAccentDark = Color(0xFF1A0B2E)
val wavoraBackground: Color get() = WavoraTone.ground
val wavoraSurface: Color get() = WavoraTone.surface1
val wavoraBorder: Color get() = WavoraTone.hairline
val wavoraTextPrimary = Color(0xFFFFFFFF)
val wavoraTextSecondary = Color(0xFFB3B3B3)
val wavoraTextDisabled = Color(0xFF6B6B6B)
val wavoraGradientStart = Color(0xFFFDC5E2)
val wavoraGradientMid = Color(0xFFEFA8D8)
val wavoraGradientEnd = Color(0xFFC9A7FF)

// ===== Material Theme aliases (reference brand tokens) =====
val md_theme_dark_primary = wavoraPrimary
val md_theme_dark_onPrimary = Color(0xFFFFFFFF)
val md_theme_dark_primaryContainer = Color(0xFF2D1050)
val md_theme_dark_onPrimaryContainer = Color(0xFFDAE2FF)
val md_theme_dark_secondary = wavoraSecondary
val md_theme_dark_onSecondary = Color(0xFF2A3042)
val md_theme_dark_secondaryContainer = wavoraAccentDark
val md_theme_dark_onSecondaryContainer = Color(0xFFDCE2F9)
val md_theme_dark_tertiary = Color(0xFFE1BBDD)
val md_theme_dark_onTertiary = Color(0xFF412741)
val md_theme_dark_tertiaryContainer = Color(0xFF5A3D59)
val md_theme_dark_onTertiaryContainer = Color(0xFFFED7F9)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = wavoraBackground
val md_theme_dark_onBackground = Color(0xFFFFFFFF)
val md_theme_dark_surface = wavoraSurface
val md_theme_dark_onSurface = Color(0xFFFFFFFF)
val md_theme_dark_surfaceVariant = wavoraBorder
val md_theme_dark_onSurfaceVariant = wavoraTextSecondary
val md_theme_dark_outline = wavoraBorder
val md_theme_dark_inverseOnSurface = Color(0xFF1B1B1F)
val md_theme_dark_inverseSurface = Color(0xFFFFFFFF)
val md_theme_dark_inversePrimary = Color(0xFF3A5BA9)
val md_theme_dark_shadow = Color(0xFF000000)
val md_theme_dark_surfaceTint = wavoraSecondary
val md_theme_dark_outlineVariant = wavoraBorder
val md_theme_dark_scrim = Color(0xFF000000)

// ===== Utility colors =====
val colorPrimaryDark = Color(0x19000000)
val back_button_color = Color(0x197E7E7E)
val checkedFilterColor = Color(0xff4d4848)
val shimmerBackground = Color(0x7E383737)
val shimmerLine = Color(0xFF4D4848)
val overlay = Color(0x32242424)
val blackMoreOverlay = Color(0x8f242424)
val seed: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary
val bottomBarSeedDark = Color(0xff53a7d0)
val customGray = Color(0x40ECECEC)
val customDarkGray = Color(0x40383535)
val white = Color(0xFFFFFFFF)
val transparent = Color(0x00000000)
