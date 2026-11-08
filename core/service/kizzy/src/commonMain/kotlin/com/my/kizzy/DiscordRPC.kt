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

// Discord rejects the whole activity when name/details/state are shorter than 2 or longer than 128 chars,
// which is why long titles made the presence silently disappear. Fields are also cleaned so the same text
// is not repeated and play/view counters ("1.2M views") never end up in the artist line.
private const val RPC_FIELD_MAX = 128
private const val RPC_FIELD_MIN = 2
private const val RPC_BUTTON_LABEL_MAX = 32
private val rpcCounterRegex =
    Regex(
        """(?i)(\d[\d\s.,]*\s*(k|m|b|тыс|млн|млрд|к)?\.?\s*(views?|plays?|listens?|просмотр\p{L}*|прослушива\p{L}*|подписчик\p{L}*|subscribers?))""",
    )

private fun rpcClean(value: String?): String = value.orEmpty().replace(Regex("\\s+"), " ").trim()

private fun rpcField(value: String): String {
    val cleaned = rpcClean(value)
    val limited = if (cleaned.length > RPC_FIELD_MAX) cleaned.take(RPC_FIELD_MAX - 1).trimEnd() + "…" else cleaned
    // A single character is rejected too: pad with a blank braille char.
    return if (limited.length in 1 until RPC_FIELD_MIN) limited + "\u2800" else limited
}

private fun rpcArtists(artists: List<String>?): String =
    artists.orEmpty()
        .map(::rpcClean)
        .filter { it.isNotBlank() && !rpcCounterRegex.containsMatchIn(it) }
        .distinctBy { it.lowercase() }
        .joinToString(", ")

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
        // Layout: header "Listening to <title - artists>", line 1 = title, line 2 = artists, line 3 = album.
        val title = rpcClean(song.title)
        val artists = rpcArtists(song.artistName)
        val album =
            rpcClean(song.albumName)
                .takeUnless {
                    it.isBlank() || it.equals("Album", ignoreCase = true) ||
                        it.equals(title, ignoreCase = true) || it.equals(artists, ignoreCase = true)
                }
        val trackAndArtists = listOf(title, artists).filter(String::isNotBlank).joinToString(" - ")
        val stateLine = artists.takeIf { it.isNotBlank() }?.let { if (isPlaying) it else "⏸ $it" }

        setActivity(
            name = rpcField(trackAndArtists.ifBlank { "Wavora" }),
            details = title.takeIf { it.isNotBlank() }?.let(::rpcField),
            state = stateLine?.let(::rpcField),
            largeImage = song.thumbnails?.let { RpcImage.ExternalImage(it) },
            smallImage = settings.smallImageUrl
                .takeIf { it.isNotBlank() }
                ?.let { RpcImage.ExternalImage(it) },
            largeText = album?.let(::rpcField),
            smallText = "Wavora",
            buttons = buildList {
                val buttonOneUrl = settings.buttonOneUrl.replace("{videoId}", song.videoId)
                if (settings.buttonOneEnabled && settings.buttonOneLabel.isNotBlank() && buttonOneUrl.isNotBlank()) {
                    add(settings.buttonOneLabel.trim().take(RPC_BUTTON_LABEL_MAX) to buttonOneUrl)
                }
                if (settings.buttonTwoEnabled && settings.buttonTwoLabel.isNotBlank() && settings.buttonTwoUrl.isNotBlank()) {
                    add(settings.buttonTwoLabel.trim().take(RPC_BUTTON_LABEL_MAX) to settings.buttonTwoUrl)
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