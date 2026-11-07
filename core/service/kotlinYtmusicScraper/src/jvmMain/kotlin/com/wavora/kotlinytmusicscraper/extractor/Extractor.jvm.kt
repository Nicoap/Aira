package com.wavora.scraper.extractor

import com.wavora.scraper.models.SongItem
import com.wavora.scraper.models.response.DownloadProgress
import com.wavora.logger.Logger
import dev.maxrave.pipepipe.extractor.NewPipe
import dev.maxrave.pipepipe.extractor.ServiceList
import dev.maxrave.pipepipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.NewPipe as BraveNewPipe
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo as BraveStreamInfo
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private const val TAG = "Extractor"

// Measured (2026-11-07 log): 6 of 6 PipePipe results failed the libVLC-style open-ended Range probe, so
// every uncached track paid ~2.3-5.4 s for PipePipe + probe before BravePipe even started (~half of the
// total 4-11 s). After PIPE_FAIL_LIMIT consecutive failures skip PipePipe for PIPE_PAUSE, then try again.
private const val PIPE_FAIL_LIMIT = 3
private val PIPE_PAUSE = 30.minutes

actual class Extractor {
    private var newPipeDownloader = NewPipeDownloaderImpl(proxy = null)
    private var braveNewPipeDownloader = BraveNewPipeDownloaderImpl(proxy = null)

    @Volatile private var pipeFailStreak = 0

    @Volatile private var pipeSkipUntil: TimeSource.Monotonic.ValueTimeMark? = null

    private fun pipePipeSkipped(): Boolean = pipeSkipUntil?.let { !it.hasPassedNow() } ?: false

    private fun notePipePipeFailure() {
        pipeFailStreak++
        if (pipeFailStreak >= PIPE_FAIL_LIMIT) {
            pipeSkipUntil = TimeSource.Monotonic.markNow() + PIPE_PAUSE
            pipeFailStreak = 0
            Logger.w(TAG, "PipePipe failed $PIPE_FAIL_LIMIT times in a row: using BravePipe only for $PIPE_PAUSE")
        }
    }

    actual fun init() {
        NewPipe.init(newPipeDownloader)
        BraveNewPipe.init(braveNewPipeDownloader)
    }

    actual fun logIn(cookie: String?) {
        ServiceList.YouTube.tokens = cookie ?: ""
    }

    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        if (!pipePipeSkipped()) try {
            val streamInfo =
                StreamInfo.getInfo(ServiceList.YouTube, "https://music.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            val temp =
                streamsList
                    .mapNotNull {
                        (it.itagItem?.id ?: return@mapNotNull null) to it.content
                    }.toMutableList()
            val manifest = streamInfo.dashMpdUrl.takeIf { !it.isNullOrEmpty() } ?: streamInfo.hlsUrl
            if (!manifest.isNullOrEmpty()) temp.add(96 to manifest)
            val pipeResult = temp.toList()
            if (!pipeResult.hasRequiredItags()) {
                Logger.d(
                    TAG,
                    "PipePipe missing required itags for $videoId (got=${pipeResult.map { it.first }}), falling back to BravePipe",
                )
                notePipePipeFailure()
            } else if (!pipeResult.headCheckRandomStream()) {
                Logger.d(
                    TAG,
                    "PipePipe stream URL HEAD check failed (non 2xx) for $videoId, falling back to BravePipe",
                )
                notePipePipeFailure()
            } else {
                pipeFailStreak = 0
                return pipeResult
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "PipePipe extractor failed for $videoId: ${e.message}, falling back to BravePipe")
            notePipePipeFailure()
        }

        return runCatching {
            val streamInfo =
                BraveStreamInfo.getInfo(BraveServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            val temp =
                streamsList
                    .mapNotNull {
                        (it.itagItem?.id ?: return@mapNotNull null) to it.content
                    }.toMutableList()
            val manifest = streamInfo.dashMpdUrl.takeIf { !it.isNullOrEmpty() } ?: streamInfo.hlsUrl
            if (!manifest.isNullOrEmpty()) temp.add(96 to manifest)
            temp.toList()
        }.onFailure {
            Logger.w(TAG, "BravePipe extractor failed for $videoId: ${it.message}")
        }.getOrElse { emptyList() }
    }

    actual fun mergeAudioVideoDownload(filePath: String): DownloadProgress = DownloadProgress.failed("Not supported on JVM")

    actual fun saveAudioWithThumbnail(
        filePath: String,
        track: SongItem,
    ): DownloadProgress = DownloadProgress.AUDIO_DONE
}