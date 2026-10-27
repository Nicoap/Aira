package com.my.kizzy

import com.wavora.domain.model.entities.SongEntity
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class DiscordRpcSettings(
    val buttonOneLabel: String = "Listen on Wavora",
    val buttonOneUrl: String = "https://music.youtube.com/watch?v={videoId}",
    val buttonOneEnabled: Boolean = true,
    val buttonTwoLabel: String = "Visit Wavora",
    val buttonTwoUrl: String = "https://github.com/Wavora-dev/Wavora",
    val buttonTwoEnabled: Boolean = true,
    val smallImageUrl: String = "https://raw.githubusercontent.com/jdecked/twemoji/main/assets/72x72/1f3b5.png",
)

class DiscordRPC(
    token: String,
) : KizzyRPC(token) {
    @OptIn(ExperimentalTime::class)
    suspend fun updateSong(
        currentPlaybackTimeMillis: Long,
        durationMillis: Long,
        playbackSpeed: Float = 1.0f,
        song: SongEntity,
        settings: DiscordRpcSettings = DiscordRpcSettings(),
        isPlaying: Boolean = true,
    ) = runCatching {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val safePlaybackSpeed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeDurationMillis = durationMillis.coerceAtLeast(0L)
        val safePositionMillis =
            currentPlaybackTimeMillis
                .coerceAtLeast(0L)
                .let { if (safeDurationMillis > 0L) it.coerceAtMost(safeDurationMillis) else it }
        val adjustedPlaybackTime = (safePositionMillis / safePlaybackSpeed).toLong()
        val calculatedStartTime = currentTime - adjustedPlaybackTime
        val adjustedRemainingDuration = ((safeDurationMillis - safePositionMillis) / safePlaybackSpeed).toLong()
        val artists = song.artistName.orEmpty().joinToString(", ")
        val album = song.albumName?.takeUnless { it.equals("Album", ignoreCase = true) }
        val trackAndArtists = listOf(song.title, artists).filter(String::isNotBlank).joinToString(" - ")

        setActivity(
            name = trackAndArtists,
            details = song.title,
            state =
                listOf(
                    if (isPlaying) null else "Paused",
                    artists,
                    album,
                ).filterNotNull().filter(String::isNotBlank).joinToString(" • "),
            largeImage = song.thumbnails?.let { RpcImage.ExternalImage(it) },
            smallImage = settings.smallImageUrl
                .takeIf { it.isNotBlank() }
                ?.let { RpcImage.ExternalImage(it) },
            largeText = album,
            smallText = "Wavora",
            buttons = buildList {
                val buttonOneUrl = settings.buttonOneUrl.replace("{videoId}", song.videoId)
                if (settings.buttonOneEnabled && settings.buttonOneLabel.isNotBlank() && buttonOneUrl.isNotBlank()) {
                    add(settings.buttonOneLabel to buttonOneUrl)
                }
                if (settings.buttonTwoEnabled && settings.buttonTwoLabel.isNotBlank() && settings.buttonTwoUrl.isNotBlank()) {
                    add(settings.buttonTwoLabel to settings.buttonTwoUrl)
                }
            },
            type = Type.LISTENING,
            startTime = calculatedStartTime.takeIf { isPlaying && safeDurationMillis > 0L },
            endTime = (currentTime + adjustedRemainingDuration).takeIf { isPlaying && safeDurationMillis > 0L },
            applicationId = APPLICATION_ID,
        )
    }.onFailure { error ->
        if (error is kotlinx.coroutines.CancellationException) {
            throw error
        }
    }

    companion object {
        private const val APPLICATION_ID = "1530792392163328031"
    }
}