package com.wavora.app.ui.screen.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wavora.app.ui.component.DraggableItem
import com.wavora.app.ui.component.HeartCheckBox
import com.wavora.app.ui.component.LyricsView
import com.wavora.app.ui.component.QueueItemBottomSheet
import com.wavora.app.ui.component.rememberDragDropState
import com.wavora.app.ui.navigation.destination.list.AlbumDestination
import com.wavora.app.ui.navigation.destination.list.ArtistDestination
import com.wavora.app.ui.theme.LocalAppTypography
import com.wavora.app.ui.theme.themedWavoraLogoPainter
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.app.viewModel.UIEvent
import com.wavora.domain.mediaservice.handler.QueueData
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.domain.mediaservice.handler.RepeatState
import com.wavora.domain.model.model.streams.TimeLine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.lyrics
import wavora.composeapp.generated.resources.no_lyrics_for_track
import wavora.composeapp.generated.resources.now_playing
import wavora.composeapp.generated.resources.queue

// Flat, neutral ground; one tone step per surface; the accent only marks state
// (progress, the current line, the current track, active toggles).
private val Ground = Color(0xFF0B0B0B)
private val Surface1 = Color(0xFF131313)
private val Surface2 = Color(0xFF1B1B1B)
private val Hairline = Color(0xFF262626)
private val InkText = Color(0xFFF2F2F2)
private val Muted = Color(0xFF8C8C8C)

private val Numeric = TextStyle(fontFeatureSettings = "tnum")

private fun clock(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/**
 * Maximized Now Playing for wide windows: the side panel stretched over the whole window as
 * a real player.
 *
 *  - left: artwork (or the video), title with direct links to the artist(s) and the album,
 *    seek bar, transport (shuffle / previous / play / next / repeat) and volume;
 *  - right: "Lyrics" and "Queue" tabs. Lyrics use the shared synced view; the queue is a dense
 *    list with the current track marked, drag-to-reorder (long press) and a link to the artist
 *    on every row.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlayingMaximizedScreen(
    navController: NavController,
    onCollapse: () -> Unit,
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val showVideo by sharedViewModel.getVideo.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val song = nowPlaying?.songEntity
    val artistNames = song?.artistName.orEmpty()
    val artistIds = song?.artistId.orEmpty()

    fun openArtist(id: String?) {
        if (id.isNullOrBlank()) return
        onCollapse()
        navController.navigate(ArtistDestination(channelId = id))
    }

    fun openAlbum(id: String?) {
        if (id.isNullOrBlank()) return
        onCollapse()
        navController.navigate(AlbumDestination(browseId = id))
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Ground),
    ) {
        // Top bar: collapse | context | product mark
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.FullscreenExit, contentDescription = null, tint = InkText)
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.now_playing),
                    style = LocalAppTypography.current.bodySmall.copy(color = Muted),
                )
                Text(
                    text = screenData.playlistName,
                    style = LocalAppTypography.current.bodyMedium.copy(color = InkText, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Image(
                painter = themedWavoraLogoPainter(),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Wavora",
                style = LocalAppTypography.current.bodyMedium.copy(color = InkText, fontWeight = FontWeight.SemiBold),
            )
        }

        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 40.dp, end = 40.dp, bottom = 32.dp),
        ) {
            val wide = maxWidth >= 860.dp
            val player: @Composable (Modifier) -> Unit = { mod ->
                PlayerBlock(
                    modifier = mod,
                    sharedViewModel = sharedViewModel,
                    title = screenData.nowPlayingTitle,
                    thumbnailUrl = screenData.thumbnailURL,
                    isVideo = screenData.isVideo,
                    showVideo = showVideo,
                    artistNames = artistNames,
                    artistIds = artistIds,
                    fallbackArtist = screenData.artistName,
                    fallbackArtistId = screenData.songInfoData?.authorId,
                    albumName = song?.albumName,
                    albumId = song?.albumId,
                    isLiked = controller.isLiked,
                    isPlaying = controller.isPlaying,
                    isShuffle = controller.isShuffle,
                    repeatState = controller.repeatState,
                    isNextAvailable = controller.isNextAvailable,
                    isPreviousAvailable = controller.isPreviousAvailable,
                    volume = controller.volume,
                    timeline = timeline,
                    onArtist = ::openArtist,
                    onAlbum = ::openAlbum,
                )
            }
            val side: @Composable (Modifier) -> Unit = { mod ->
                SidePane(
                    modifier = mod,
                    tab = tab,
                    onTab = { tab = it },
                    sharedViewModel = sharedViewModel,
                    onArtist = ::openArtist,
                )
            }
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                ) {
                    player(Modifier.weight(0.85f).fillMaxHeight())
                    side(Modifier.weight(1.15f).fillMaxHeight())
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    player(Modifier.weight(1f).fillMaxWidth())
                    side(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerBlock(
    modifier: Modifier,
    sharedViewModel: SharedViewModel,
    title: String,
    thumbnailUrl: String?,
    isVideo: Boolean,
    showVideo: Boolean,
    artistNames: List<String>,
    artistIds: List<String>,
    fallbackArtist: String,
    fallbackArtistId: String?,
    albumName: String?,
    albumId: String?,
    isLiked: Boolean,
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeatState: RepeatState,
    isNextAvailable: Boolean,
    isPreviousAvailable: Boolean,
    volume: Float,
    timeline: TimeLine,
    onArtist: (String?) -> Unit,
    onAlbum: (String?) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val videoMode = isVideo && showVideo

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        // Artwork / video: the dominant element, sized by whichever dimension runs out first.
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val boxW = maxWidth
            val boxH = maxHeight
            val artW: Dp
            val artH: Dp
            if (videoMode) {
                val byWidth = boxW * 9f / 16f
                if (byWidth <= boxH) {
                    artW = boxW
                    artH = byWidth
                } else {
                    artH = boxH
                    artW = boxH * 16f / 9f
                }
            } else {
                val side = if (boxW < boxH) boxW else boxH
                artW = side
                artH = side
            }
            Box(
                modifier =
                    Modifier
                        .size(artW, artH)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface1),
            ) {
                if (videoMode) {
                    com.wavora.app.expect.ui.MediaPlayerViewWithSubtitle(
                        playerName = com.wavora.common.Config.MAIN_PLAYER,
                        modifier = Modifier.fillMaxSize(),
                        shouldShowSubtitle = false,
                        shouldPip = false,
                        shouldScaleDownSubtitle = true,
                        timelineState = timeline,
                        lyricsData = null,
                        translatedLyricsData = null,
                        isInPipMode = false,
                        mainTextStyle = LocalAppTypography.current.bodyLarge,
                        translatedTextStyle = LocalAppTypography.current.bodyMedium,
                    )
                } else {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalPlatformContext.current)
                                .data(thumbnailUrl)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .diskCacheKey(thumbnailUrl + "BIGGER")
                                .crossfade(250)
                                .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Title + links + like
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LocalAppTypography.current.titleLarge.copy(color = InkText, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(verticalArrangement = Arrangement.Center) {
                    if (artistNames.isNotEmpty()) {
                        artistNames.forEachIndexed { index, name ->
                            LinkText(
                                text = name,
                                style = LocalAppTypography.current.bodyLarge,
                                onClick = { onArtist(artistIds.getOrNull(index)) },
                            )
                            if (index < artistNames.lastIndex) {
                                Text(", ", style = LocalAppTypography.current.bodyLarge.copy(color = Muted))
                            }
                        }
                    } else if (fallbackArtist.isNotBlank()) {
                        LinkText(
                            text = fallbackArtist,
                            style = LocalAppTypography.current.bodyLarge,
                            onClick = { onArtist(fallbackArtistId) },
                        )
                    }
                    if (!albumName.isNullOrBlank()) {
                        Text("  ·  ", style = LocalAppTypography.current.bodyLarge.copy(color = Muted))
                        LinkText(
                            text = albumName,
                            style = LocalAppTypography.current.bodyLarge,
                            onClick = { onAlbum(albumId) },
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            HeartCheckBox(
                checked = isLiked,
                size = 30,
                onStateChange = { sharedViewModel.onUIEvent(UIEvent.ToggleLike) },
            )
        }

        Spacer(Modifier.height(20.dp))

        SeekBar(
            timeline = timeline,
            accent = accent,
            onSeek = { sharedViewModel.onUIEvent(UIEvent.UpdateProgress(it)) },
        )

        Spacer(Modifier.height(8.dp))

        // Transport
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { sharedViewModel.onUIEvent(UIEvent.Shuffle) }) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = if (isShuffle) accent else Muted)
            }
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { sharedViewModel.onUIEvent(UIEvent.Previous) },
                enabled = isPreviousAvailable,
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = if (isPreviousAvailable) InkText else Hairline,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier =
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(InkText)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sharedViewModel.onUIEvent(UIEvent.PlayPause) },
                contentAlignment = Alignment.Center,
            ) {
                if (timeline.loading && !isPlaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = Ground,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Ground,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { sharedViewModel.onUIEvent(UIEvent.Next) },
                enabled = isNextAvailable,
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = if (isNextAvailable) InkText else Hairline,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { sharedViewModel.onUIEvent(UIEvent.Repeat) }) {
                Icon(
                    imageVector = if (repeatState == RepeatState.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = null,
                    tint = if (repeatState == RepeatState.None) Muted else accent,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Volume
        var volumeDragging by remember { mutableStateOf(false) }
        var volumeValue by remember { mutableFloatStateOf(volume) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (volume <= 0.001f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            ThinSlider(
                value = if (volumeDragging) volumeValue else volume,
                valueRange = 0f..1f,
                accent = InkText,
                modifier = Modifier.width(160.dp),
                onValueChange = {
                    volumeDragging = true
                    volumeValue = it
                    sharedViewModel.onUIEvent(UIEvent.UpdateVolume(it))
                },
                onValueChangeFinished = { volumeDragging = false },
            )
        }
    }
}

@Composable
private fun SeekBar(
    timeline: TimeLine,
    accent: Color,
    onSeek: (Float) -> Unit,
) {
    var sliding by remember { mutableStateOf(false) }
    var slideValue by remember { mutableFloatStateOf(0f) }
    val progress =
        if (timeline.total > 0) {
            (timeline.current.toFloat() / timeline.total.toFloat() * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
    val shown = if (sliding) slideValue else progress
    val shownMs = if (sliding && timeline.total > 0) (slideValue / 100f * timeline.total).toLong() else timeline.current

    Column(modifier = Modifier.fillMaxWidth()) {
        ThinSlider(
            value = shown,
            valueRange = 0f..100f,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = {
                sliding = true
                slideValue = it
            },
            onValueChangeFinished = {
                onSeek(slideValue)
                sliding = false
            },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(shownMs), style = LocalAppTypography.current.bodySmall.merge(Numeric).copy(color = Muted))
            Text(clock(timeline.total), style = LocalAppTypography.current.bodySmall.merge(Numeric).copy(color = Muted))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        interactionSource = source,
        modifier = modifier.height(20.dp).pointerHoverIcon(PointerIcon.Hand),
        thumb = {
            Box(
                Modifier
                    .size(if (hovered) 12.dp else 0.dp)
                    .clip(CircleShape)
                    .background(InkText),
            )
        },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Hairline),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(if (hovered) accent else accent.copy(alpha = 0.9f)),
                )
            }
        },
    )
}

@Composable
private fun LinkText(
    text: String,
    style: TextStyle,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Text(
        text = text,
        style =
            style.copy(
                color = if (hovered) InkText else Muted,
                textDecoration = if (hovered) TextDecoration.Underline else TextDecoration.None,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = source, indication = null, onClick = onClick),
    )
}

@Composable
private fun SidePane(
    modifier: Modifier,
    tab: Int,
    onTab: (Int) -> Unit,
    sharedViewModel: SharedViewModel,
    onArtist: (String?) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1),
    ) {
        Row(
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            PaneTab(stringResource(Res.string.lyrics), tab == 0, accent) { onTab(0) }
            PaneTab(stringResource(Res.string.queue), tab == 1, accent) { onTab(1) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            if (tab == 0) {
                val lyrics = screenData.lyricsData
                if (lyrics != null) {
                    LyricsView(
                        lyricsData = lyrics,
                        timeLine = sharedViewModel.timeline,
                        onLineClick = { f -> sharedViewModel.onUIEvent(UIEvent.UpdateProgress(f)) },
                        modifier = Modifier.fillMaxSize(),
                        backgroundColor = Surface1,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.no_lyrics_for_track),
                        style = LocalAppTypography.current.bodyMedium.copy(color = Muted),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                MaximizedQueue(
                    modifier = Modifier.fillMaxSize(),
                    sharedViewModel = sharedViewModel,
                    onArtist = onArtist,
                )
            }
        }
    }
}

@Composable
private fun PaneTab(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = LocalAppTypography.current.bodyLarge.copy(color = if (selected) InkText else Muted, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Box(
            Modifier
                .height(2.dp)
                .width(24.dp)
                .background(if (selected) accent else Color.Transparent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MaximizedQueue(
    modifier: Modifier,
    sharedViewModel: SharedViewModel,
    onArtist: (String?) -> Unit,
    musicServiceHandler: MediaPlayerHandler = koinInject<MediaPlayerHandler>(),
    dataStoreManager: DataStoreManager = koinInject(),
) {
    val accent = MaterialTheme.colorScheme.primary
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val dragDropState =
        rememberDragDropState(lazyListState) { from, to ->
            coroutineScope.launch { musicServiceHandler.swap(from, to) }
        }
    var overscrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showItemSheet by rememberSaveable { mutableStateOf(false) }
    var sheetIndex by rememberSaveable { mutableIntStateOf(0) }
    val songEntity by sharedViewModel.nowPlayingState.map { it?.songEntity }.collectAsStateWithLifecycle(null)
    val queueData by musicServiceHandler.queueData.collectAsStateWithLifecycle()
    val queue by remember { derivedStateOf { queueData?.data?.listTracks ?: emptyList() } }
    val loadMoreState by remember { derivedStateOf { queueData?.queueState ?: QueueData.StateSource.STATE_CREATED } }
    val endless by dataStoreManager.endlessQueue.map { it == DataStoreManager.TRUE }.collectAsStateWithLifecycle(false)

    val nearEnd by remember {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 3 && info.totalItemsCount > 0
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { nearEnd }.collect {
            if (it && loadMoreState == QueueData.StateSource.STATE_INITIALIZED) musicServiceHandler.loadMore()
        }
    }
    LaunchedEffect(Unit) {
        val current = musicServiceHandler.currentOrderIndex().takeIf { i -> i > -1 } ?: 0
        lazyListState.requestScrollToItem((current - 1).coerceAtLeast(0))
    }

    if (showItemSheet) {
        QueueItemBottomSheet(
            onDismiss = { showItemSheet = false },
            index = sheetIndex,
            musicServiceHandler = musicServiceHandler,
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = queue.size.toString(),
                style = LocalAppTypography.current.bodySmall.merge(Numeric).copy(color = Muted),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Endless",
                style = LocalAppTypography.current.bodySmall.copy(color = Muted),
                modifier = Modifier.padding(end = 8.dp),
            )
            Switch(
                checked = endless,
                onCheckedChange = { coroutineScope.launch { dataStoreManager.setEndlessQueue(it) } },
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = accent,
                        checkedThumbColor = Ground,
                        uncheckedTrackColor = Hairline,
                        uncheckedThumbColor = Muted,
                        uncheckedBorderColor = Hairline,
                    ),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { change, offset ->
                                    change.consume()
                                    dragDropState.onDrag(offset = offset)
                                    if (overscrollJob?.isActive == true) return@detectDragGesturesAfterLongPress
                                    dragDropState
                                        .checkForOverScroll()
                                        .takeIf { it != 0f }
                                        ?.let {
                                            overscrollJob =
                                                coroutineScope.launch {
                                                    dragDropState.state.animateScrollBy(it * 1.3f)
                                                }
                                        } ?: run { overscrollJob?.cancel() }
                                },
                                onDragStart = { offset -> dragDropState.onDragStart(offset) },
                                onDragEnd = {
                                    dragDropState.onDragInterrupted(true)
                                    overscrollJob?.cancel()
                                },
                                onDragCancel = {
                                    dragDropState.onDragInterrupted()
                                    overscrollJob?.cancel()
                                },
                            )
                        },
            ) {
                itemsIndexed(queue, key = { i, t -> i.toString() + t.videoId }) { index, track ->
                    DraggableItem(dragDropState = dragDropState, index = index, modifier = Modifier) { _ ->
                        QueueRow(
                            title = track.title,
                            artist = track.artists?.joinToString(", ") { it.name }.orEmpty(),
                            artistId = track.artists?.firstOrNull()?.id,
                            duration = track.duration.orEmpty(),
                            thumbnail = track.thumbnails?.lastOrNull()?.url,
                            isCurrent = track.videoId == songEntity?.videoId,
                            accent = accent,
                            onPlay = { musicServiceHandler.playMediaItemInMediaSource(index) },
                            onMore = {
                                sheetIndex = index
                                showItemSheet = true
                            },
                            onArtist = onArtist,
                        )
                    }
                }
            }
            com.wavora.app.expect.ui.VerticalScrollBar(
                modifier = Modifier.align(Alignment.CenterEnd),
                scrollState = lazyListState,
            )
        }
    }
}

@Composable
private fun QueueRow(
    title: String,
    artist: String,
    artistId: String?,
    duration: String,
    thumbnail: String?,
    isCurrent: Boolean,
    accent: Color,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    onArtist: (String?) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(if (hovered) Surface2 else Color.Transparent)
                .hoverable(source)
                .clickable(interactionSource = source, indication = null, onClick = onPlay)
                .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isCurrent) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = LocalAppTypography.current.bodyLarge.copy(color = if (isCurrent) accent else InkText, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.isNotBlank()) {
                LinkText(text = artist, style = LocalAppTypography.current.bodySmall, onClick = { onArtist(artistId) })
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = duration,
            style = LocalAppTypography.current.bodySmall.merge(Numeric).copy(color = Muted),
        )
        IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = if (hovered || isCurrent) InkText else Color.Transparent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
