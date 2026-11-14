package com.wavora.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wavora.app.expect.ui.sampleScreenPixel
import com.wavora.app.ui.theme.LocalAppTypography
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Пикер цвета вместо ручного ввода HEX.
 *
 * WHY: пять строк «введи #RRGGBB» — это единственный способ поменять тему, и он требует
 * знать коды цветов наизусть. Здесь три ползунка (оттенок / насыщенность / яркость),
 * живой предпросмотр, поле HEX для точного значения и пипетка с экрана на десктопе.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialHex: String,
    showEyedropper: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val startColor = remember(initialHex) { parseHexColor(initialHex) ?: Color(0xFFA259FF) }
    val startHsv = remember(startColor) { startColor.toHsv() }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var saturation by remember { mutableFloatStateOf(startHsv[1]) }
    var brightness by remember { mutableFloatStateOf(startHsv[2]) }
    var hexText by remember { mutableStateOf(startColor.toHexString()) }
    var pickCountdown by remember { mutableIntStateOf(0) }

    val currentColor = hsvToColor(hue, saturation, brightness)

    // Пипетка: даём три секунды, чтобы увести курсор с диалога на нужный цвет, затем читаем пиксель.
    LaunchedEffect(pickCountdown) {
        if (pickCountdown > 0) {
            delay(1_000)
            if (pickCountdown == 1) {
                sampleScreenPixel()?.let { picked ->
                    val hsv = picked.toHsv()
                    hue = hsv[0]
                    saturation = hsv[1]
                    brightness = hsv[2]
                    hexText = picked.toHexString()
                }
                pickCountdown = 0
            } else {
                pickCountdown -= 1
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            hexText = input
                            parseHexColor(input)?.let { parsed ->
                                val hsv = parsed.toHsv()
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2]
                            }
                        },
                        label = { Text("HEX") },
                        singleLine = true,
                        textStyle =
                            LocalAppTypography.current.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        modifier = Modifier.weight(1f),
                    )
                }
                GradientSlider(
                    label = "Оттенок",
                    value = hue,
                    range = 0f..360f,
                    brush =
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red,
                            ),
                        ),
                    onChange = {
                        hue = it
                        hexText = hsvToColor(hue, saturation, brightness).toHexString()
                    },
                )
                GradientSlider(
                    label = "Насыщенность",
                    value = saturation,
                    range = 0f..1f,
                    brush =
                        Brush.horizontalGradient(
                            listOf(
                                hsvToColor(hue, 0f, brightness),
                                hsvToColor(hue, 1f, brightness),
                            ),
                        ),
                    onChange = {
                        saturation = it
                        hexText = hsvToColor(hue, saturation, brightness).toHexString()
                    },
                )
                GradientSlider(
                    label = "Яркость",
                    value = brightness,
                    range = 0f..1f,
                    brush =
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black,
                                hsvToColor(hue, saturation, 1f),
                            ),
                        ),
                    onChange = {
                        brightness = it
                        hexText = hsvToColor(hue, saturation, brightness).toHexString()
                    },
                )
                if (showEyedropper) {
                    OutlinedButton(onClick = { if (pickCountdown == 0) pickCountdown = 3 }) {
                        Text(
                            text =
                                if (pickCountdown > 0) {
                                    "Наведите курсор на цвет: $pickCountdown"
                                } else {
                                    "Пипетка"
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor.toHexString()) }) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = LocalAppTypography.current.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            track = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush),
                )
            },
        )
    }
}

/** Разбирает `#RRGGBB` / `RRGGBB`; возвращает null для «auto» и мусора. */
internal fun parseHexColor(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6) return null
    val rgb = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

internal fun Color.toHexString(): String =
    "#" + hexComponent(red) + hexComponent(green) + hexComponent(blue)

private fun hexComponent(value: Float): String =
    (value * 255f).toInt().coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')

internal fun Color.toHsv(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min
    val h =
        when {
            delta == 0f -> 0f
            max == r -> (((g - b) / delta) % 6f + 6f) % 6f * 60f
            max == g -> (((b - r) / delta) + 2f) * 60f
            else -> (((r - g) / delta) + 4f) * 60f
        }
    val s = if (max == 0f) 0f else delta / max
    return floatArrayOf(h, s, max)
}

/** Локальная HSV→RGB: в этом же пакете уже есть своя `hsvToColor`, поэтому функция приватная. */
private fun hsvToColor(
    h: Float,
    s: Float,
    v: Float,
): Color {
    val c = v * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = v - c
    val rgb =
        when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    return Color(rgb.first + m, rgb.second + m, rgb.third + m)
}
