package com.wavora.app.ui.theme

import org.jetbrains.compose.resources.StringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.theme_preset_custom
import wavora.composeapp.generated.resources.theme_preset_graphite
import wavora.composeapp.generated.resources.theme_preset_midnight
import wavora.composeapp.generated.resources.theme_preset_nord
import wavora.composeapp.generated.resources.theme_preset_paper
import wavora.composeapp.generated.resources.theme_preset_sepia

/**
 * Готовые темы вместо выбора «светлая / тёмная / системная».
 *
 * WHY: приложение и так рисует интерфейс из пользовательских цветов, поэтому отдельный
 * переключатель светлой и тёмной темы ничего не решал — он выбирал лишь базовую схему, которую
 * всё равно перетирали HEX-значения. Теперь тема — это набор из четырёх цветов; «светлая» и
 * «тёмная» получаются автоматически по яркости фона (см. `AppTheme`).
 *
 * Названия пресетов хранятся ресурсами, а не строками: иначе список тем оставался русским
 * при любом выбранном языке приложения.
 */
data class WavoraThemePreset(
    val id: String,
    val labelRes: StringResource,
    val background: String,
    val surface: String,
    val text: String,
    val primary: String,
)

/**
 * Пять заготовок. Значения намеренно подобраны так, чтобы контраст текста к фону был высоким,
 * а поверхность отличалась от фона на один тон — иначе карточки и панели сливаются.
 */
val WavoraThemePresets =
    listOf(
        WavoraThemePreset(
            id = "graphite",
            labelRes = Res.string.theme_preset_graphite,
            background = "#0F0E27",
            surface = "#06050F",
            text = "#F2F2F2",
            primary = "#FDC5E2",
        ),
        WavoraThemePreset(
            id = "midnight",
            labelRes = Res.string.theme_preset_midnight,
            background = "#0A1020",
            surface = "#121A2E",
            text = "#E8EEF9",
            primary = "#4C8DFF",
        ),
        WavoraThemePreset(
            id = "nord",
            labelRes = Res.string.theme_preset_nord,
            background = "#2E3440",
            surface = "#3B4252",
            text = "#ECEFF4",
            primary = "#88C0D0",
        ),
        WavoraThemePreset(
            id = "sepia",
            labelRes = Res.string.theme_preset_sepia,
            background = "#F4ECD8",
            surface = "#EBE0C7",
            text = "#3B2F2F",
            primary = "#B5651D",
        ),
        WavoraThemePreset(
            id = "paper",
            labelRes = Res.string.theme_preset_paper,
            background = "#FAFAF7",
            surface = "#F1F1EC",
            text = "#1F1F1F",
            primary = "#2D6A4F",
        ),
    )

/** Ресурс подписи для набора цветов, который не совпал ни с одной заготовкой. */
val WAVORA_CUSTOM_THEME_LABEL: StringResource = Res.string.theme_preset_custom

/**
 * Определяет, какой пресет сейчас применён, сравнивая сохранённые HEX-значения.
 * Возвращает `null`, если цвета свои.
 */
fun findThemePreset(
    background: String,
    surface: String,
    text: String,
    primary: String,
): WavoraThemePreset? {
    val bg = background.trim().uppercase()
    val sf = surface.trim().uppercase()
    val tx = text.trim().uppercase()
    val pr = primary.trim().uppercase()
    return WavoraThemePresets.firstOrNull { preset ->
        preset.background.uppercase() == bg &&
            preset.surface.uppercase() == sf &&
            preset.text.uppercase() == tx &&
            preset.primary.uppercase() == pr
    }
}
