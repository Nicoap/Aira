package com.wavora.app.ui.theme

/**
 * Готовые темы вместо выбора «светлая / тёмная / системная».
 *
 * WHY: приложение и так рисует интерфейс из пользовательских цветов, поэтому отдельный
 * переключатель светлой и тёмной темы ничего не решал — он выбирал лишь базовую схему, которую
 * всё равно перетирали HEX-значения. Теперь тема — это набор из четырёх цветов; «светлая» и
 * «тёмная» получаются автоматически по яркости фона (см. `AppTheme`).
 */
data class WavoraThemePreset(
    val id: String,
    val label: String,
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
            label = "Графит",
            background = "#0B0B0B",
            surface = "#131313",
            text = "#F2F2F2",
            primary = "#A259FF",
        ),
        WavoraThemePreset(
            id = "midnight",
            label = "Полночь",
            background = "#0A1020",
            surface = "#121A2E",
            text = "#E8EEF9",
            primary = "#4C8DFF",
        ),
        WavoraThemePreset(
            id = "nord",
            label = "Норд",
            background = "#2E3440",
            surface = "#3B4252",
            text = "#ECEFF4",
            primary = "#88C0D0",
        ),
        WavoraThemePreset(
            id = "sepia",
            label = "Сепия",
            background = "#F4ECD8",
            surface = "#EBE0C7",
            text = "#3B2F2F",
            primary = "#B5651D",
        ),
        WavoraThemePreset(
            id = "paper",
            label = "Бумага",
            background = "#FAFAF7",
            surface = "#F1F1EC",
            text = "#1F1F1F",
            primary = "#2D6A4F",
        ),
    )

/** Подпись для набора цветов, который не совпал ни с одной заготовкой. */
const val WAVORA_CUSTOM_THEME_LABEL = "Своя"

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
