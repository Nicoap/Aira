package com.wavora.app.ui.mini_player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wavora.app.ui.component.hoverMarquee
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.app.viewModel.UIEvent
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.baseline_close_24
import wavora.composeapp.generated.resources.baseline_favorite_24
import wavora.composeapp.generated.resources.baseline_favorite_border_24
import wavora.composeapp.generated.resources.baseline_pause_24
import wavora.composeapp.generated.resources.baseline_play_arrow_24
import wavora.composeapp.generated.resources.baseline_skip_next_24
import wavora.composeapp.generated.resources.baseline_skip_previous_24
import wavora.composeapp.generated.resources.holder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Size of the island at rest and when opened. The window follows these (see MiniPlayerWindow). */
internal val IslandCompactSize = DpSize(264.dp, 36.dp)
internal val IslandExpandedSize = DpSize(424.dp, 178.dp)
internal const val IslandAnimationMs = 320

private val IslandFlare = 11.dp
private val IslandCorner = 24.dp
private val IslandBlack = Color(0xFF000000)
private val IslandTextSecondary = Color(0xFFA6A6A6)

/**
 * Notch-like outline: flat top that merges into the screen edge through small concave "flares", rounded
 * bottom corners. The flares are what make the shape read as hanging from the top of the screen.
 */
private class IslandShape(
    private val flare: Dp,
    private val corner: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val f = with(density) { flare.toPx() }.coerceAtMost(h / 2f)
        val r = with(density) { corner.toPx() }.coerceAtMost(h - f).coerceAtMost((w - 2f * f) / 2f)
        val path =
            Path().apply {
                moveTo(0f, 0f)
                // concave top-left flare: centre (0, f)
                arcTo(Rect(-f, 0f, f, 2f * f), 270f, 90f, false)
                lineTo(f, h - r)
                // bottom-left corner
                arcTo(Rect(f, h - 2f * r, f + 2f * r, h), 180f, -90f, false)
                lineTo(w - f - r, h)
                // bottom-right corner
                arcTo(Rect(w - f - 2f * r, h - 2f * r, w - f, h), 90f, -90f, false)
                lineTo(w - f, f)
                // concave top-right flare: centre (w, f)
                arcTo(Rect(w - f, 0f, w + f, 2f * f), 180f, 90f, false)
                close()
            }
        return Outline.Generic(path)
    }
}

/**
 * Drives the visualizer. It is a playback-synchronised animation (it runs while a track plays and settles
 * when paused), not a spectrum analysis: libVLC hands its decoded audio straight to the sound card and does
 * not expose it, so there is nothing real to analyse. Ticks at 20 Hz in the island's own window only.
 */
@Composable
private fun rememberVisualizerPhase(playing: Boolean): State<Float> {
    val phase = remember { mutableStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            delay(50)
            phase.value += 0.18f
        }
    }
    return phase
}

@Composable
private fun IslandBars(
    playing: Boolean,
    phase: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(width = 18.dp, height = 16.dp)) {
        val bars = 4
        val barWidth = 3.dp.toPx()
        val gap = (size.width - bars * barWidth) / (bars - 1)
        for (i in 0 until bars) {
            val level = if (playing) 0.3f + 0.7f * abs(sin(phase * (1.1f + 0.43f * i) + i * 1.7f)) else 0.25f
            val barHeight = size.height * level
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/** Thin waveform along the bottom edge of the island. */
@Composable
private fun IslandWave(
    playing: Boolean,
    phase: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val amplitude = if (playing) 0.65f + 0.35f * abs(sin(phase * 1.7f)) else 0.1f
        val mid = size.height / 2f
        val step = 3.dp.toPx()
        val path = Path()
        path.moveTo(0f, mid)
        var x = 0f
        while (x <= size.width) {
            val t = x / size.width
            val taper = sin(PI.toFloat() * t)
            val wave = 0.6f * sin(t * 18f + phase * 3.1f) + 0.4f * sin(t * 7f - phase * 2.3f)
            path.lineTo(x, mid + amplitude * taper * mid * 0.9f * wave)
            x += step
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun IslandCover(
    url: String?,
    size: Dp,
    corner: Dp,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(Res.drawable.holder),
        error = painterResource(Res.drawable.holder),
        modifier = Modifier.size(size).clip(RoundedCornerShape(corner)),
    )
}

@Composable
private fun IslandSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    fillColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .height(18.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { onValueChange((it.x / size.width).coerceIn(0f, 1f)) }
                }.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { onValueChange((it.x / size.width).coerceIn(0f, 1f)) },
                        onDrag = { change, _ ->
                            change.consume()
                            onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                        },
                    )
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(3.dp).clip(CircleShape).background(fillColor))
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun IslandIconButton(
    resId: org.jetbrains.compose.resources.DrawableResource,
    description: String,
    size: Dp,
    iconSize: Dp,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(resId), description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun CompactIslandContent(
    title: String,
    thumbnail: String?,
    hasTrack: Boolean,
    playing: Boolean,
    phase: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = IslandFlare + 8.dp, end = IslandFlare + 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IslandCover(thumbnail, 22.dp, 6.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            text = if (hasTrack) title else "Wavora",
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(9.dp))
        IslandBars(playing, phase, accent)
    }
}

@Composable
private fun ExpandedIslandContent(
    sharedViewModel: SharedViewModel,
    expandOnHover: Boolean,
    onToggleExpandMode: () -> Unit,
    onClose: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val nowPlaying by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val onEvent = sharedViewModel::onUIEvent
    val hasTrack = nowPlaying.nowPlayingTitle.isNotBlank()

    Box(modifier = modifier.padding(start = IslandFlare + 12.dp, end = IslandFlare + 12.dp, top = 12.dp)) {
        if (!hasTrack) {
            Text(
                text = "Ничего не играет",
                modifier = Modifier.align(Alignment.Center),
                color = IslandTextSecondary,
                fontSize = 13.sp,
            )
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IslandCover(nowPlaying.thumbnailURL, 64.dp, 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Text(
                            text = nowPlaying.nowPlayingTitle,
                            modifier = Modifier.fillMaxWidth().hoverMarquee(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = nowPlaying.artistName,
                            modifier = Modifier.fillMaxWidth().hoverMarquee(),
                            color = IslandTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                        IslandIconButton(Res.drawable.baseline_close_24, "Close", 22.dp, 14.dp, IslandTextSecondary, onClose)
                        IslandIconButton(
                            resId = if (controller.isLiked) Res.drawable.baseline_favorite_24 else Res.drawable.baseline_favorite_border_24,
                            description = "Like",
                            size = 30.dp,
                            iconSize = 20.dp,
                            tint = if (controller.isLiked) accent else Color.White,
                        ) { onEvent(UIEvent.ToggleLike) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(timeline.current), color = IslandTextSecondary, fontSize = 10.sp)
                    IslandSlider(
                        value = if (timeline.total > 0) timeline.current.toFloat() / timeline.total.toFloat() else 0f,
                        onValueChange = { onEvent(UIEvent.UpdateProgress(it * 100f)) },
                        fillColor = Color.White,
                        enabled = timeline.total > 0,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatTime(timeline.total), color = IslandTextSecondary, fontSize = 10.sp)
                }
                Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IslandIconButton(
                            Res.drawable.baseline_skip_previous_24,
                            "Previous",
                            36.dp,
                            24.dp,
                            tint = if (controller.isPreviousAvailable) Color.White else Color.White.copy(alpha = 0.3f),
                        ) { if (controller.isPreviousAvailable) onEvent(UIEvent.Previous) }
                        Box(
                            modifier =
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { onEvent(UIEvent.PlayPause) }
                                    .pointerHoverIcon(PointerIcon.Hand),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (controller.isPlaying) Res.drawable.baseline_pause_24 else Res.drawable.baseline_play_arrow_24,
                                    ),
                                contentDescription = "Play/Pause",
                                tint = IslandBlack,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        IslandIconButton(
                            Res.drawable.baseline_skip_next_24,
                            "Next",
                            36.dp,
                            24.dp,
                            tint = if (controller.isNextAvailable) Color.White else Color.White.copy(alpha = 0.3f),
                        ) { if (controller.isNextAvailable) onEvent(UIEvent.Next) }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (expandOnHover) "Наведение" else "Клик",
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable(onClick = onToggleExpandMode)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            color = IslandTextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (controller.volume > 0f) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = "Volume",
                            tint = IslandTextSecondary,
                            modifier =
                                Modifier
                                    .size(18.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onEvent(UIEvent.UpdateVolume(if (controller.volume > 0f) 0f else 1f)) },
                        )
                        IslandSlider(
                            value = controller.volume,
                            onValueChange = { onEvent(UIEvent.UpdateVolume(it)) },
                            fillColor = IslandTextSecondary,
                            modifier = Modifier.width(56.dp).padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The island itself. It hangs from the top edge of the screen: a small black capsule with the cover, the title
 * and a level meter, which grows into a full player. All sizes are driven by one animation progress so the
 * outline, the content and the window stay in step.
 */
@Composable
internal fun DynamicIsland(
    sharedViewModel: SharedViewModel,
    expanded: Boolean,
    expandOnHover: Boolean,
    onToggleExpandMode: () -> Unit,
    onExpandClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowPlaying by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val hasTrack = nowPlaying.nowPlayingTitle.isNotBlank()
    val accent = MaterialTheme.colorScheme.primary

    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(IslandAnimationMs, easing = FastOutSlowInEasing),
        label = "islandProgress",
    )
    val width = lerp(IslandCompactSize.width, IslandExpandedSize.width, progress)
    val height = lerp(IslandCompactSize.height, IslandExpandedSize.height, progress)
    val expandedAlpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
    val compactAlpha = (1f - progress * 2.2f).coerceIn(0f, 1f)

    val playing = controller.isPlaying && hasTrack
    val phaseState = rememberVisualizerPhase(playing)

    Box(
        modifier =
            modifier
                .size(width, height)
                .clip(IslandShape(IslandFlare, IslandCorner))
                .background(IslandBlack)
                .pointerInput(expanded, expandOnHover) {
                    if (!expanded && !expandOnHover) detectTapGestures { onExpandClick() }
                },
    ) {
        if (compactAlpha > 0.01f) {
            CompactIslandContent(
                title = nowPlaying.nowPlayingTitle,
                thumbnail = nowPlaying.thumbnailURL,
                hasTrack = hasTrack,
                playing = playing,
                phase = phaseState.value,
                accent = accent,
                modifier =
                    Modifier
                        .requiredSize(IslandCompactSize.width, IslandCompactSize.height)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = compactAlpha },
            )
        }
        if (expandedAlpha > 0.01f) {
            ExpandedIslandContent(
                sharedViewModel = sharedViewModel,
                expandOnHover = expandOnHover,
                onToggleExpandMode = onToggleExpandMode,
                onClose = onClose,
                accent = accent,
                modifier =
                    Modifier
                        .requiredSize(IslandExpandedSize.width, IslandExpandedSize.height)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = expandedAlpha },
            )
        }
        IslandWave(
            playing = playing,
            phase = phaseState.value,
            color = accent,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = IslandFlare + 14.dp)
                    .height(8.dp),
        )
    }
}
