package com.wavora.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

/**
 * Animated equalizer shown for the track that is currently playing.
 *
 * WHY it is drawn here instead of a Lottie composition (`Compottie.IterateForever`):
 * a live Lottie painter advances on every frame, and Compose Desktop then re-renders the
 * WHOLE window on every vsync for as long as the equalizer exists. On a 165 Hz display that
 * alone cost ~40 % of a CPU core while music was playing, and it competes with video
 * playback for the compositor. This version ticks on a small [FRAME_MILLIS] timer instead:
 * ~9 updates per second are plenty for a bouncing-bar icon, and the window is only
 * invalidated those 9 times instead of 165.
 */
@Composable
fun AudioPlayingIndicator(
    size: Dp,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            delay(FRAME_MILLIS)
            frame = (frame + 1) % FRAMES.size
        }
    }

    val heights = if (playing) FRAMES[frame] else RESTING_FRAME
    Canvas(modifier = modifier.size(size)) {
        val bars = heights.size
        val gap = this.size.width / (bars * 3f)
        val barWidth = (this.size.width - gap * (bars - 1)) / bars
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
        heights.forEachIndexed { index, fraction ->
            val barHeight = (this.size.height * fraction).coerceAtLeast(barWidth)
            drawRoundRect(
                color = tint,
                topLeft = Offset(index * (barWidth + gap), this.size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

/** Кадры эквалайзера: доля высоты каждой из четырёх полосок. */
private val FRAMES =
    arrayOf(
        floatArrayOf(0.45f, 0.9f, 0.6f, 0.8f),
        floatArrayOf(0.7f, 0.5f, 1.0f, 0.55f),
        floatArrayOf(0.95f, 0.65f, 0.45f, 0.9f),
        floatArrayOf(0.5f, 1.0f, 0.75f, 0.6f),
        floatArrayOf(0.8f, 0.45f, 0.9f, 1.0f),
        floatArrayOf(0.6f, 0.85f, 0.5f, 0.7f),
        floatArrayOf(1.0f, 0.6f, 0.85f, 0.5f),
        floatArrayOf(0.55f, 0.75f, 0.95f, 0.85f),
    )

private val RESTING_FRAME = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)

private const val FRAME_MILLIS = 110L
