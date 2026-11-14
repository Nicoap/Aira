package com.wavora.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Wavora design tokens: one place for the neutral colour ramp, the spacing scale and the corner
 * radii. New UI should use these instead of ad-hoc Color(0x...) / N.dp values.
 */

/**
 * Нейтральная рампа интерфейса, посчитанная из выбранной темы.
 *
 * WHY отдельный тип: раньше `WavoraTone` был набором констант под тёмную тему, поэтому боковая
 * панель плеера и очередь оставались тёмными в любой светлой теме и игнорировали выбранные
 * пользователем цвета. Теперь значения считает `AppTheme` и кладёт сюда.
 */
@Immutable
data class WavoraPalette(
    val ground: Color,
    val surface1: Color,
    val surface2: Color,
    val hairline: Color,
    val ink: Color,
    val muted: Color,
)

/** Запасная тёмная рампа: используется до первой композиции темы. */
val DefaultWavoraPalette =
    WavoraPalette(
        ground = Color(0xFF0B0B0B),
        surface1 = Color(0xFF131313),
        surface2 = Color(0xFF1B1B1B),
        hairline = Color(0xFF262626),
        ink = Color(0xFFF2F2F2),
        muted = Color(0xFF8C8C8C),
    )

/**
 * Flat, neutral ramp (no colour tint): one tone step per surface level. The accent colour
 * (MaterialTheme.colorScheme.primary) is reserved for state: progress, the current line, the
 * current track, active toggles.
 *
 * Значения — состояние: `AppTheme` пересчитывает их при смене темы, и все читатели
 * (включая не-Compose код) видят актуальные цвета без переписывания вызовов.
 */
object WavoraTone {
    var ground by mutableStateOf(DefaultWavoraPalette.ground)
        private set

    var surface1 by mutableStateOf(DefaultWavoraPalette.surface1)
        private set

    var surface2 by mutableStateOf(DefaultWavoraPalette.surface2)
        private set

    var hairline by mutableStateOf(DefaultWavoraPalette.hairline)
        private set

    var ink by mutableStateOf(DefaultWavoraPalette.ink)
        private set

    var muted by mutableStateOf(DefaultWavoraPalette.muted)
        private set

    /** Применяет палитру темы. Вызывается из `AppTheme` один раз на смену цветов. */
    fun apply(palette: WavoraPalette) {
        if (ground == palette.ground &&
            surface1 == palette.surface1 &&
            surface2 == palette.surface2 &&
            hairline == palette.hairline &&
            ink == palette.ink &&
            muted == palette.muted
        ) {
            return
        }
        ground = palette.ground
        surface1 = palette.surface1
        surface2 = palette.surface2
        hairline = palette.hairline
        ink = palette.ink
        muted = palette.muted
    }
}

/**
 * Строит нейтральную рампу из трёх базовых цветов темы.
 *
 * Уровни поверхностей получаются подмешиванием текстового цвета к фону, поэтому светлая тема
 * автоматически даёт светлые панели, а тёмная — тёмные, без второй палитры.
 */
fun wavoraPalette(
    background: Color,
    surface: Color,
    text: Color,
): WavoraPalette =
    WavoraPalette(
        ground = background,
        surface1 = surface,
        surface2 = lerp(surface, text, 0.10f),
        hairline = lerp(background, text, 0.16f),
        ink = text,
        muted = lerp(text, background, 0.28f),
    )

private fun lerp(
    start: Color,
    stop: Color,
    fraction: Float,
): Color =
    Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction,
    )

/** 4-pt spacing scale for padding, gaps and margins. Icon and control sizes are not spacing. */
object WavoraSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
}

/** Corner radii by role. */
object WavoraRadius {
    /** Small row thumbnails and inner chips. */
    val thumb = 4.dp

    /** Cover art, video frames, cards. */
    val art = 8.dp

    /** Panels, sheets, dialogs. */
    val panel = 12.dp
}
