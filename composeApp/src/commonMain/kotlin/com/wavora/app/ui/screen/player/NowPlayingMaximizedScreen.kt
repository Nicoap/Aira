package com.wavora.app.ui.screen.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.wavora.app.ui.component.hoverMarquee
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
import androidx.compose.runtime.collectAsState
import com.wavora.app.ui.component.HeartCheckBox
import com.wavora.domain.repository.SongRepository
import com.wavora.app.ui.component.LyricsView
import com.wavora.app.ui.component.onSecondaryClick
import com.wavora.domain.utils.toSongEntity
import com.wavora.app.ui.component.rememberDragDropState
import com.wavora.app.ui.navigation.destination.list.AlbumDestination
import com.wavora.app.ui.navigation.destination.list.ArtistDestination
import com.wavora.app.ui.theme.LocalAppTypography
import com.wavora.app.ui.theme.WavoraRadius
import com.wavora.app.ui.theme.WavoraSpace
import com.wavora.app.ui.theme.WavoraTone
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ThumbsUpDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalUriHandler
import com.wavora.app.expect.toggleMiniPlayer
import com.wavora.app.extension.parseTimestampToMilliseconds
import com.wavora.app.ui.component.AIBadge
import com.wavora.app.ui.component.AddToPlaylistModalBottomSheet
import com.wavora.app.ui.component.AudioPlayingIndicator
import com.wavora.app.ui.component.DescriptionView
import com.wavora.app.ui.component.InfoPlayerBottomSheet
import com.wavora.app.ui.component.NowPlayingBottomSheet
import com.wavora.app.ui.component.SimpleTooltip
import com.wavora.app.ui.component.VoteLyricsDialog
import com.wavora.app.viewModel.LyricsProvider
import com.wavora.app.viewModel.NowPlayingBottomSheetUIEvent
import com.wavora.app.viewModel.NowPlayingBottomSheetViewModel
import org.koin.compose.viewmodel.koinViewModel
import wavora.composeapp.generated.resources.collapse_now_playing
import wavora.composeapp.generated.resources.more
import wavora.composeapp.generated.resources.song_info
import wavora.composeapp.generated.resources.description
import wavora.composeapp.generated.resources.no_description
import wavora.composeapp.generated.resources.rate_lyrics
import wavora.composeapp.generated.resources.line_synced
import wavora.composeapp.generated.resources.rich_synced
import wavora.composeapp.generated.resources.unsynced
import wavora.composeapp.generated.resources.lyrics_provider_wavora
import wavora.composeapp.generated.resources.lyrics_provider_lrc
import wavora.composeapp.generated.resources.lyrics_provider_youtube
import wavora.composeapp.generated.resources.spotify_lyrics_provider
import wavora.composeapp.generated.resources.offline_mode
import wavora.composeapp.generated.resources.lyrics_provider_betterlyrics
import wavora.composeapp.generated.resources.published_at
import wavora.composeapp.generated.resources.view_count
import wavora.composeapp.generated.resources.like_and_dislike
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
fun NowPlayingPanel(
    navController: NavController,
    compact: Boolean,
    onCollapse: () -> Unit,
    onMaximize: (() -> Unit)? = null,
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val showVideo by sharedViewModel.getVideo.collectAsStateWithLifecycle()
    val lyricsVoteState by sharedViewModel.lyricsVoteState.collectAsStateWithLifecycle()
    val translatedVoteState by sharedViewModel.translatedVoteState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }
    var showAddToPlaylist by rememberSaveable { mutableStateOf(false) }
    var showVoteDialog by rememberSaveable { mutableStateOf(false) }

    val song = nowPlaying?.songEntity
    val artistNames = song?.artistName.orEmpty()
    val artistIds = song?.artistId.orEmpty()

    // The compact side panel stays open while you navigate; the maximized view steps aside so the
    // destination page is visible.
    fun openArtist(id: String?) {
        if (id.isNullOrBlank()) return
        if (!compact) onCollapse()
        navController.navigate(ArtistDestination(channelId = id))
    }

    fun openAlbum(id: String?) {
        if (id.isNullOrBlank()) return
        if (!compact) onCollapse()
        navController.navigate(AlbumDestination(browseId = id))
    }

    val canVoteLyrics =
        screenData.lyricsData?.lyricsProvider == LyricsProvider.SIMPMUSIC &&
            screenData.lyricsData
                ?.lyrics
                ?.simpMusicLyrics != null
    val canVoteTranslatedLyrics =
        screenData.lyricsData?.translatedLyrics?.second == LyricsProvider.SIMPMUSIC &&
            screenData.lyricsData
                ?.translatedLyrics
                ?.first
                ?.simpMusicLyrics != null

    if (showMoreSheet) {
        NowPlayingBottomSheet(
            onDismiss = { showMoreSheet = false },
            navController = navController,
            onNavigateToOtherScreen = { if (!compact) onCollapse() },
            song = null, // current track
            setSleepTimerEnable = true,
            changeMainLyricsProviderEnable = true,
        )
    }
    if (showInfoSheet) {
        InfoPlayerBottomSheet(onDismiss = { showInfoSheet = false })
    }
    if (showAddToPlaylist) {
        val viewModel: NowPlayingBottomSheetViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            viewModel.resetPlaylists()
            viewModel.setSongEntity(null) // current track
        }
        AddToPlaylistModalBottomSheet(
            isBottomSheetVisible = true,
            listLocalPlaylist = uiState.listLocalPlaylist,
            listYouTubePlaylist = uiState.listYouTubePlaylist,
            onDismiss = { showAddToPlaylist = false },
            onClick = { playlist ->
                viewModel.onUIEvent(NowPlayingBottomSheetUIEvent.AddToPlaylist(playlist.id))
                showAddToPlaylist = false
            },
            onYTPlaylistClick = { playlist ->
                viewModel.onUIEvent(NowPlayingBottomSheetUIEvent.AddToYouTubePlaylist(playlist.browseId))
                showAddToPlaylist = false
            },
            videoId = uiState.songUIState.videoId,
        )
    }
    if (showVoteDialog) {
        VoteLyricsDialog(
            canVoteLyrics = canVoteLyrics,
            canVoteTranslatedLyrics = canVoteTranslatedLyrics,
            lyricsVoteState = lyricsVoteState,
            translatedLyricsVoteState = translatedVoteState,
            onVoteLyrics = { upvote -> sharedViewModel.voteLyrics(upvote) },
            onVoteTranslatedLyrics = { upvote -> sharedViewModel.voteTranslatedLyrics(upvote) },
            onDismiss = { showVoteDialog = false },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(WavoraTone.ground),
    ) {
        PanelHeader(
            compact = compact,
            context = screenData.playlistName,
            onCollapse = onCollapse,
            onMaximize = onMaximize,
            onMiniPlayer = { toggleMiniPlayer() },
            onMore = { showMoreSheet = true },
        )

        val hPad = if (compact) WavoraSpace.lg else WavoraSpace.xxxl
        val bPad = if (compact) WavoraSpace.lg else WavoraSpace.xxl
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = hPad, end = hPad, bottom = bPad),
        ) {
            val wide = !compact && maxWidth >= 860.dp
            // Compact: the artwork takes at most ~40% of the height so the tabs below keep room.
            val artHeight = (maxHeight * 0.4f).coerceAtMost(maxWidth)
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
                    compact = compact,
                    artHeight = artHeight,
                    onAddToPlaylist = { showAddToPlaylist = true },
                    onInfo = { showInfoSheet = true },
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
                    canVote = canVoteLyrics || canVoteTranslatedLyrics,
                    onVote = { showVoteDialog = true },
                    onArtist = ::openArtist,
                    navController = navController,
                )
            }
            when {
                wide -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(56.dp),
                    ) {
                        player(Modifier.weight(0.85f).fillMaxHeight())
                        side(Modifier.weight(1.15f).fillMaxHeight())
                    }
                }

                compact -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(WavoraSpace.lg),
                    ) {
                        player(Modifier.fillMaxWidth())
                        side(Modifier.weight(1f).fillMaxWidth())
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(WavoraSpace.xl),
                    ) {
                        player(Modifier.weight(1f).fillMaxWidth())
                        side(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Header: collapse | context | maximize (compact only) | mini player | more. */
@Composable
private fun PanelHeader(
    compact: Boolean,
    context: String,
    onCollapse: () -> Unit,
    onMaximize: (() -> Unit)?,
    onMiniPlayer: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = if (compact) WavoraSpace.md else WavoraSpace.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SimpleTooltip(stringResource(Res.string.collapse_now_playing)) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = if (compact) Icons.AutoMirrored.Rounded.ArrowForwardIos else Icons.Filled.FullscreenExit,
                    contentDescription = stringResource(Res.string.collapse_now_playing),
                    tint = WavoraTone.ink,
                )
            }
        }
        Spacer(Modifier.width(WavoraSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.now_playing),
                style = LocalAppTypography.current.bodySmall.copy(color = WavoraTone.muted),
            )
            Text(
                text = context,
                style = LocalAppTypography.current.bodyMedium.copy(color = WavoraTone.ink, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (compact && onMaximize != null) {
            SimpleTooltip("Maximize") {
                IconButton(onClick = onMaximize) {
                    Icon(Icons.Filled.OpenInFull, contentDescription = "Maximize", tint = WavoraTone.ink)
                }
            }
        }
        SimpleTooltip("Mini Player") {
            IconButton(onClick = onMiniPlayer) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Mini Player", tint = WavoraTone.ink)
            }
        }
        SimpleTooltip(stringResource(Res.string.more)) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.more), tint = WavoraTone.ink)
            }
        }
        if (!compact) {
            Spacer(Modifier.width(WavoraSpace.sm))
            Image(
                painter = themedWavoraLogoPainter(),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = null,
            )
            Spacer(Modifier.width(WavoraSpace.sm))
            Text(
                text = "Wavora",
                style = LocalAppTypography.current.bodyMedium.copy(color = WavoraTone.ink, fontWeight = FontWeight.SemiBold),
            )
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
    compact: Boolean,
    artHeight: Dp,
    onAddToPlaylist: () -> Unit,
    onInfo: () -> Unit,
    onArtist: (String?) -> Unit,
    onAlbum: (String?) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val videoMode = isVideo && showVideo
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val canvasData = screenData.canvasData
    val canvasMode = !videoMode && canvasData != null

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        // Artwork / video: the dominant element, sized by whichever dimension runs out first.
        BoxWithConstraints(
            modifier =
                (if (compact) Modifier.height(artHeight) else Modifier.weight(1f))
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
            } else if (canvasMode) {
                // Portrait box for a vertical (9:16) Spotify Canvas loop.
                val byHeight = boxH * 9f / 16f
                if (byHeight <= boxW) {
                    artH = boxH
                    artW = byHeight
                } else {
                    artW = boxW
                    artH = boxW * 16f / 9f
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
                        .clip(RoundedCornerShape(WavoraRadius.art))
                        .background(WavoraTone.surface1),
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
                    // Canvas loop sits on top of the cover: the cover stays visible until the first frame.
                    if (canvasMode && canvasData != null) {
                        if (canvasData.isVideo) {
                            com.wavora.app.expect.ui.MediaPlayerView(
                                url = canvasData.url,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            AsyncImage(
                                model = canvasData.url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Title + links + like
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LocalAppTypography.current.titleLarge.copy(color = WavoraTone.ink, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 22.sp else 26.sp),
                    maxLines = 1,
                    modifier = Modifier.hoverMarquee(),
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
                                Text(", ", style = LocalAppTypography.current.bodyLarge.copy(color = WavoraTone.muted))
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
                        Text("  ·  ", style = LocalAppTypography.current.bodyLarge.copy(color = WavoraTone.muted))
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
            Spacer(Modifier.width(WavoraSpace.xs))
            SimpleTooltip("Add to playlist") {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = WavoraTone.muted)
                }
            }
            SimpleTooltip(stringResource(Res.string.song_info)) {
                IconButton(onClick = onInfo) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.song_info), tint = WavoraTone.muted)
                }
            }
        }

        if (!compact) {
            PlayerControls(
                sharedViewModel = sharedViewModel,
                timeline = timeline,
                accent = accent,
                isShuffle = isShuffle,
                isPlaying = isPlaying,
                isPreviousAvailable = isPreviousAvailable,
                isNextAvailable = isNextAvailable,
                repeatState = repeatState,
                volume = volume,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    sharedViewModel: SharedViewModel,
    timeline: TimeLine,
    accent: Color,
    isShuffle: Boolean,
    isPlaying: Boolean,
    isPreviousAvailable: Boolean,
    isNextAvailable: Boolean,
    repeatState: RepeatState,
    volume: Float,
) {
    Column {
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
                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = if (isShuffle) accent else WavoraTone.muted)
            }
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { sharedViewModel.onUIEvent(UIEvent.Previous) },
                enabled = isPreviousAvailable,
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = if (isPreviousAvailable) WavoraTone.ink else WavoraTone.hairline,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier =
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(WavoraTone.ink)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { sharedViewModel.onUIEvent(UIEvent.PlayPause) },
                contentAlignment = Alignment.Center,
            ) {
                if (timeline.loading && !isPlaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = WavoraTone.ground,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = WavoraTone.ground,
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
                    tint = if (isNextAvailable) WavoraTone.ink else WavoraTone.hairline,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { sharedViewModel.onUIEvent(UIEvent.Repeat) }) {
                Icon(
                    imageVector = if (repeatState == RepeatState.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = null,
                    tint = if (repeatState == RepeatState.None) WavoraTone.muted else accent,
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
                tint = WavoraTone.muted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            ThinSlider(
                value = if (volumeDragging) volumeValue else volume,
                valueRange = 0f..1f,
                accent = WavoraTone.ink,
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
            Text(clock(shownMs), style = LocalAppTypography.current.bodySmall.merge(Numeric).copy(color = WavoraTone.muted))
            Text(clock(timeline.total), style = LocalAppTypography.current.bodySmall.merge(Numeric).copy(color = WavoraTone.muted))
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
                    .background(WavoraTone.ink),
            )
        },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WavoraTone.hairline),
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
                color = if (hovered) WavoraTone.ink else WavoraTone.muted,
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
    canVote: Boolean,
    onVote: () -> Unit,
    onArtist: (String?) -> Unit,
    navController: NavController,
) {
    val accent = MaterialTheme.colorScheme.primary
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(WavoraRadius.panel))
                .background(WavoraTone.surface1),
    ) {
        Row(
            modifier = Modifier.padding(start = WavoraSpace.xl, end = WavoraSpace.xl, top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(WavoraSpace.xl),
        ) {
            PaneTab(stringResource(Res.string.lyrics), tab == 0, accent) { onTab(0) }
            PaneTab(stringResource(Res.string.queue), tab == 1, accent) { onTab(1) }
            PaneTab(stringResource(Res.string.description), tab == 2, accent) { onTab(2) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WavoraTone.hairline),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                0 -> LyricsTab(sharedViewModel = sharedViewModel, canVote = canVote, onVote = onVote)
                1 ->
                    MaximizedQueue(
                        modifier = Modifier.fillMaxSize(),
                        sharedViewModel = sharedViewModel,
                        onArtist = onArtist,
                        navController = navController,
                    )
                else -> AboutTab(sharedViewModel = sharedViewModel, onArtist = onArtist)
            }
        }
    }
}

@Composable
private fun LyricsTab(
    sharedViewModel: SharedViewModel,
    canVote: Boolean,
    onVote: () -> Unit,
) {
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val data = screenData.lyricsData
    if (data == null) {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = stringResource(Res.string.no_lyrics_for_track),
                style = LocalAppTypography.current.bodyMedium.copy(color = WavoraTone.muted),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    val syncLabel =
        when (data.lyrics?.syncType) {
            "LINE_SYNCED" -> stringResource(Res.string.line_synced)
            "RICH_SYNCED" -> stringResource(Res.string.rich_synced)
            else -> stringResource(Res.string.unsynced)
        }
    val providerLabel =
        when (data.lyricsProvider) {
            LyricsProvider.SIMPMUSIC -> stringResource(Res.string.lyrics_provider_wavora)
            LyricsProvider.LRCLIB -> stringResource(Res.string.lyrics_provider_lrc)
            LyricsProvider.YOUTUBE -> stringResource(Res.string.lyrics_provider_youtube)
            LyricsProvider.SPOTIFY -> stringResource(Res.string.spotify_lyrics_provider)
            LyricsProvider.OFFLINE -> stringResource(Res.string.offline_mode)
            LyricsProvider.BETTER_LYRICS -> stringResource(Res.string.lyrics_provider_betterlyrics)
            else -> ""
        }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = WavoraSpace.xl, end = WavoraSpace.sm, top = WavoraSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (data.translatedLyrics?.second == LyricsProvider.AI) {
                AIBadge()
                Spacer(Modifier.width(WavoraSpace.sm))
            }
            Text(
                text = listOf(syncLabel, providerLabel).filter { it.isNotBlank() }.joinToString("  \u00B7  "),
                style = LocalAppTypography.current.bodySmall.copy(color = WavoraTone.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canVote) {
                SimpleTooltip(stringResource(Res.string.rate_lyrics)) {
                    IconButton(onClick = onVote) {
                        Icon(
                            imageVector = Icons.Rounded.ThumbsUpDown,
                            contentDescription = stringResource(Res.string.rate_lyrics),
                            tint = WavoraTone.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        LyricsView(
            lyricsData = data,
            timeLine = sharedViewModel.timeline,
            onLineClick = { f -> sharedViewModel.onUIEvent(UIEvent.UpdateProgress(f)) },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = WavoraSpace.xl),
            backgroundColor = WavoraTone.surface1,
        )
    }
}

private fun groupDigits(digits: String): String = digits.reversed().chunked(3).joinToString(" ").reversed()

/** "Description" tab: artist card, stats and the description (timestamps seek, links open). */
@Composable
private fun AboutTab(
    sharedViewModel: SharedViewModel,
    onArtist: (String?) -> Unit,
) {
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val info = screenData.songInfoData
    if (info == null) {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = stringResource(Res.string.no_description),
                style = LocalAppTypography.current.bodyMedium.copy(color = WavoraTone.muted),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    val artistId =
        nowPlaying
            ?.songEntity
            ?.artistId
            ?.firstOrNull()
            ?.takeIf { it.isNotEmpty() } ?: info.authorId
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WavoraSpace.xl),
        verticalArrangement = Arrangement.spacedBy(WavoraSpace.lg),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(WavoraRadius.art))
                    .background(WavoraTone.surface2)
                    .clickable { onArtist(artistId) }
                    .padding(WavoraSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(info.authorThumbnail)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(info.authorThumbnail)
                        .crossfade(250)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(WavoraSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = info.author ?: "",
                    style = LocalAppTypography.current.titleSmall.copy(color = WavoraTone.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = info.subscribers ?: "",
                    style = LocalAppTypography.current.bodySmall.copy(color = WavoraTone.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(WavoraSpace.xs)) {
            Text(
                text = stringResource(Res.string.published_at, info.uploadDate ?: ""),
                style = LocalAppTypography.current.bodyMedium.copy(color = WavoraTone.muted),
            )
            Text(
                text = stringResource(Res.string.view_count, info.viewCount?.toString()?.let(::groupDigits) ?: ""),
                style = LocalAppTypography.current.bodyMedium.copy(color = WavoraTone.muted),
            )
            Text(
                text = stringResource(Res.string.like_and_dislike, info.like ?: 0, info.dislike ?: 0),
                style = LocalAppTypography.current.bodyMedium.copy(color = WavoraTone.muted),
            )
        }
        DescriptionView(
            text = info.description ?: "",
            onTimeClicked = { raw ->
                val timestamp = parseTimestampToMilliseconds(raw)
                if (timestamp != 0.0 && timestamp < timeline.total) {
                    sharedViewModel.onUIEvent(UIEvent.UpdateProgress(((timestamp * 100) / timeline.total).toFloat()))
                }
            },
            onURLClicked = { url -> uriHandler.openUri(url) },
        )
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
            style = LocalAppTypography.current.bodyLarge.copy(color = if (selected) WavoraTone.ink else WavoraTone.muted, fontWeight = FontWeight.Medium),
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
    navController: NavController,
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
    // Once the queue has loaded and the playing track is known, bring it into view.
    val queueAndTrackReady = queue.isNotEmpty() && songEntity != null
    LaunchedEffect(queueAndTrackReady) {
        if (!queueAndTrackReady) return@LaunchedEffect
        val playingIndex = queue.indexOfFirst { it.videoId == songEntity?.videoId }
        if (playingIndex > 0) lazyListState.scrollToItem(playingIndex)
    }

    val duplicateIndices by remember(queue) {
        derivedStateOf {
            val seen = HashSet<String>()
            queue.mapIndexedNotNull { index, track -> if (seen.add(track.videoId)) null else index }
        }
    }

    if (showItemSheet) {
        val queueSize = queue.size
        NowPlayingBottomSheet(
            onDismiss = { showItemSheet = false },
            navController = navController,
            song = queue.getOrNull(sheetIndex)?.toSongEntity(),
            onQueueMoveToStart = if (sheetIndex > 0 && sheetIndex < queueSize) {
                { coroutineScope.launch { musicServiceHandler.swap(sheetIndex, 0) } }
            } else {
                null
            },
            onQueueMoveToEnd = if (sheetIndex in 0 until queueSize - 1) {
                { coroutineScope.launch { musicServiceHandler.swap(sheetIndex, queueSize - 1) } }
            } else {
                null
            },
            onQueueRemove = if (sheetIndex in 0 until queueSize) {
                { musicServiceHandler.removeMediaItem(sheetIndex) }
            } else {
                null
            },
            onQueueRemoveDuplicates = if (duplicateIndices.isNotEmpty()) {
                { duplicateIndices.sortedDescending().forEach { musicServiceHandler.removeMediaItem(it) } }
            } else {
                null
            },
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WavoraSpace.xl, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = queue.size.toString(),
                style = LocalAppTypography.current.bodySmall.merge(Numeric).copy(color = WavoraTone.muted),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Endless",
                style = LocalAppTypography.current.bodySmall.copy(color = WavoraTone.muted),
                modifier = Modifier.padding(end = 8.dp),
            )
            Switch(
                checked = endless,
                onCheckedChange = { coroutineScope.launch { dataStoreManager.setEndlessQueue(it) } },
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = accent,
                        checkedThumbColor = WavoraTone.ground,
                        uncheckedTrackColor = WavoraTone.hairline,
                        uncheckedThumbColor = WavoraTone.muted,
                        uncheckedBorderColor = WavoraTone.hairline,
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
                            videoId = track.videoId,
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
    videoId: String,
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
    // Лайк прямо в очереди: раньше сердечко было только в списках треков.
    val songRepository: SongRepository = koinInject()
    val likeScope = rememberCoroutineScope()
    val likedSong by remember(videoId) { songRepository.getSongAsFlow(videoId) }.collectAsState(initial = null)
    val isLiked = likedSong?.liked == true
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(if (hovered) WavoraTone.surface2 else Color.Transparent)
                .hoverable(source)
                .clickable(interactionSource = source, indication = null, onClick = onPlay)
                .onSecondaryClick(onMore)
                .padding(horizontal = WavoraSpace.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(WavoraRadius.thumb))
                    .background(WavoraTone.surface2),
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
                    AudioPlayingIndicator(size = 44.dp, tint = accent)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = LocalAppTypography.current.bodyLarge.copy(color = if (isCurrent) accent else WavoraTone.ink, fontWeight = FontWeight.Medium),
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
            style = LocalAppTypography.current.bodySmall.merge(Numeric).copy(color = WavoraTone.muted),
        )
        HeartCheckBox(size = 26, checked = isLiked) {
            likeScope.launch {
                songRepository.updateLikeStatus(videoId, if (isLiked) 0 else 1)
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = if (hovered || isCurrent) WavoraTone.ink else Color.Transparent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
