package com.wavora.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.kmpalette.palette.graphics.Palette
import com.kmpalette.rememberPaletteState
import com.wavora.app.expect.ui.toImageBitmap
import com.wavora.app.extension.glowAccent
import kotlin.math.max

/**
 * The most characteristic colour of the cover that is playing, or null until it is known (and when the cover
 * has no usable colour). The cover is decoded at a small size: the palette needs only a rough picture.
 */
@Composable
fun rememberCoverAccent(url: String?): Color? {
    val context = LocalPlatformContext.current
    val paletteState = rememberPaletteState()
    var loadedFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            loadedFor = null
            return@LaunchedEffect
        }
        val result =
            SingletonImageLoader
                .get(context)
                .execute(
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(128, 128)
                        .build(),
                )
        val bitmap = (result as? SuccessResult)?.image?.toImageBitmap() ?: return@LaunchedEffect
        paletteState.generate(bitmap)
        loadedFor = url
    }

    val palette = paletteState.palette
    return remember(palette, loadedFor, url) {
        if (loadedFor != url) null else palette?.vividColor()
    }
}

private fun Palette.vividColor(): Color? {
    val none = 0
    val argb =
        getVibrantColor(none).takeIf { it != none }
            ?: getLightVibrantColor(none).takeIf { it != none }
            ?: getMutedColor(none).takeIf { it != none }
            ?: getDarkVibrantColor(none).takeIf { it != none }
            ?: getDominantColor(none).takeIf { it != none }
            ?: return null
    return Color(argb)
}

/**
 * Soft light spilling from behind the cover over the whole surface, in the cover's own colour. The colour
 * changes smoothly between tracks; nothing animates while a track plays, so it costs no frames at rest.
 * A light theme gets a weaker, tinted glow, a dark one a stronger one.
 */
@Composable
fun Modifier.coverGlow(accent: Color?): Modifier {
    val background = MaterialTheme.colorScheme.background
    val light = (0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue) > 0.5f
    val target = if (accent == null) Color.Transparent else glowAccent(accent, light)
    val animated = animateColorAsState(targetValue = target, animationSpec = tween(900), label = "coverGlow")
    val strength = if (light) 0.34f else 0.46f
    return this.drawBehind {
        val color = animated.value
        if (color.alpha <= 0.01f) return@drawBehind
        val reach = max(size.width, size.height * 0.62f)
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            color.copy(alpha = color.alpha * strength),
                            color.copy(alpha = color.alpha * strength * 0.45f),
                            Color.Transparent,
                        ),
                    center = Offset(size.width / 2f, size.height * 0.3f),
                    radius = reach * 0.95f,
                ),
        )
    }
}
