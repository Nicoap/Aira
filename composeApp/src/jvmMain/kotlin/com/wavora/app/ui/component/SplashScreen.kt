package com.wavora.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2 * Math.PI).toFloat()
private val LOGO_SIZE = 190.dp
private val LOGO_SHIFT_Y = (-18).dp

/**
 * Wavora splash: "Sound Burst".
 *
 * Intro (~0.8s, everything in the accent color, no blur, no per-frame allocations):
 *  1. the logo springs in from small with a tilt and overshoots into place;
 *  2. a double shockwave ripples out of it, together with a ring of speed-streaks
 *     (a stylised sound burst);
 *  3. an equalizer settles below the logo and keeps dancing while the app really
 *     finishes initializing.
 *
 * There is NO timer tied to the real startup: the caller flips [visible] to false when
 * initialization is done. Exit is a quick (260ms) scale-up + fade, then [onExitFinished].
 *
 * All animated values are read inside draw / graphicsLayer lambdas, so animation never
 * triggers recomposition.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary

    val introAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.2f) }
    val logoTilt = remember { Animatable(-24f) }
    val glow = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { introAlpha.animateTo(1f, tween(160, easing = LinearEasing)) }
            launch { logoScale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f)) }
            launch { logoTilt.animateTo(0f, spring(dampingRatio = 0.48f, stiffness = 300f)) }
            launch { glow.animateTo(1f, tween(450, easing = EaseOutCubic)) }
            launch {
                delay(110)
                burst.animateTo(1f, tween(760, easing = EaseOutCubic))
            }
        }
    }

    LaunchedEffect(visible) {
        if (!visible) {
            exit.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            onExitFinished()
        }
    }

    // Single cheap loop that drives the idle equalizer + glow pulse.
    val idle = rememberInfiniteTransition(label = "splash_idle")
    val beat by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
        label = "beat",
    )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f + 0.16f * exit.value
                    scaleX = s
                    scaleY = s
                    alpha = introAlpha.value * (1f - exit.value)
                    transformOrigin = TransformOrigin.Center
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f + LOGO_SHIFT_Y.toPx())
            val pulse = sin(TWO_PI * beat) * 0.5f + 0.5f
            val b = burst.value

            drawGlow(center, accent, glow.value * (0.85f + 0.15f * pulse))
            drawShockwave(center, b, accent, strokeStart = 7.dp.toPx())
            drawShockwave(center, ((b - 0.18f) / 0.82f).coerceIn(0f, 1f), accent, strokeStart = 4.dp.toPx())
            drawStreaks(center, b, accent)
            drawEqualizer(center, b, beat, accent)
        }

        Image(
            painter = com.wavora.app.ui.theme.themedWavoraLogoPainter(),
            contentDescription = null,
            modifier =
                Modifier
                    .offset(y = LOGO_SHIFT_Y)
                    .size(LOGO_SIZE)
                    .graphicsLayer {
                        val pulse = sin(TWO_PI * beat) * 0.5f + 0.5f
                        val s = logoScale.value * (1f + 0.02f * pulse * burst.value)
                        scaleX = s
                        scaleY = s
                        rotationZ = logoTilt.value
                        transformOrigin = TransformOrigin.Center
                    },
        )
    }
}

private fun DrawScope.drawGlow(
    center: Offset,
    accent: Color,
    strength: Float,
) {
    if (strength <= 0f) return
    val radius = 190.dp.toPx()
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.32f * strength), Color.Transparent),
                center = center,
                radius = radius,
            ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawShockwave(
    center: Offset,
    progress: Float,
    accent: Color,
    strokeStart: Float,
) {
    if (progress <= 0f || progress >= 1f) return
    drawCircle(
        color = accent.copy(alpha = (1f - progress) * 0.6f),
        radius = lerp(70.dp.toPx(), 250.dp.toPx(), progress),
        center = center,
        style = Stroke(width = lerp(strokeStart, 1f, progress)),
    )
}

private fun DrawScope.drawStreaks(
    center: Offset,
    progress: Float,
    accent: Color,
) {
    if (progress <= 0f || progress >= 1f) return
    val count = 16
    val color = accent.copy(alpha = (1f - progress).coerceIn(0f, 1f))
    for (i in 0 until count) {
        val angle = TWO_PI * i / count + if (i % 2 == 0) 0f else 0.11f
        val reach = 150.dp.toPx() + (i % 3) * 38.dp.toPx()
        val start = lerp(78.dp.toPx(), reach, progress)
        val len = lerp(34.dp.toPx(), 4.dp.toPx(), progress) * (0.7f + (i % 3) * 0.25f)
        val dir = Offset(cos(angle), sin(angle))
        drawLine(
            color = color,
            start = center + dir * start,
            end = center + dir * (start + len),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawEqualizer(
    center: Offset,
    appear: Float,
    beat: Float,
    accent: Color,
) {
    if (appear <= 0f) return
    val bars = 5
    val barWidth = 8.dp.toPx()
    val gap = 7.dp.toPx()
    val minH = 8.dp.toPx()
    val maxH = 34.dp.toPx()
    val totalW = bars * barWidth + (bars - 1) * gap
    val left = center.x - totalW / 2f + barWidth / 2f
    val midY = center.y + 128.dp.toPx()
    val color = accent.copy(alpha = (appear * 0.9f).coerceIn(0f, 1f))
    for (i in 0 until bars) {
        val wave = sin(TWO_PI * beat + i * 1.3f) * 0.5f + 0.5f
        val h = lerp(minH, maxH, wave) * appear
        val x = left + i * (barWidth + gap)
        drawLine(
            color = color,
            start = Offset(x, midY - h / 2f),
            end = Offset(x, midY + h / 2f),
            strokeWidth = barWidth,
            cap = StrokeCap.Round,
        )
    }
}
