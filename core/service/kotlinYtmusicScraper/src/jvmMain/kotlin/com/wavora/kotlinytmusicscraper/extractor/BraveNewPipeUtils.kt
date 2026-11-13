package com.wavora.scraper.extractor

import com.wavora.logger.Logger
import com.wavora.scraper.models.YouTubeClient
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

private val REQUIRED_AUDIO_ITAGS = setOf(250, 251, 774, 141)
private val REQUIRED_VIDEO_ITAGS = setOf(137, 136, 134)

internal fun List<Pair<Int, String>>.hasRequiredItags(): Boolean {
    val itags = this.mapTo(HashSet()) { it.first }
    val hasAudio = REQUIRED_AUDIO_ITAGS.any { it in itags }
    val hasVideo = REQUIRED_VIDEO_ITAGS.any { it in itags }
    return hasAudio && hasVideo
}

private val streamHealthCheckClient: OkHttpClient by lazy {
    OkHttpClient
        .Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()
}

/**
 * Pick one random URL whose itag belongs to [REQUIRED_AUDIO_ITAGS] or [REQUIRED_VIDEO_ITAGS] and
 * HEAD it. Returns true only when the response code is in the 200..299 range. We only health-check
 * required-itag URLs because those are the ones the player will actually use; lower-quality
 * extras can stay unverified.
 */
internal fun List<Pair<Int, String>>.headCheckRandomStream(): Boolean {
    val required = REQUIRED_AUDIO_ITAGS + REQUIRED_VIDEO_ITAGS
    // Сначала аудио-itag: именно его проигрывает плеер для обычных треков.
    val candidate =
        this.filter { it.first in REQUIRED_AUDIO_ITAGS }.randomOrNull()
            ?: this.filter { it.first in required }.randomOrNull()
            ?: return false
    val t0 = System.nanoTime()
    // HEAD на googlevideo всегда 403, поэтому проверяем GET и не читаем тело. Range — такой же ограниченный,
    // какой запрашивает StreamProxy: открытый `bytes=0-` на «грязных» IP отдаёт 403 даже для рабочих ссылок.
    val result = probe(candidate.second, PROBE_RANGE, YouTubeClient.USER_AGENT_WEB)
    val ok = result.code in 200..299
    Logger.d(
        "Extractor",
        "Stream probe itag=${candidate.first} rangeGET=${result.code} ok=$ok in ${(System.nanoTime() - t0) / 1_000_000}ms",
    )
    return ok
}

private const val PROBE_RANGE = "bytes=0-1048575"

private class ProbeResult(
    val code: Int,
    val body: String,
)

private fun probe(
    url: String,
    range: String,
    userAgent: String,
): ProbeResult =
    runCatching {
        val request =
            okhttp3.Request
                .Builder()
                .get()
                .url(url)
                .header("Range", range)
                .header("User-Agent", userAgent)
        streamHealthCheckClient
            .newCall(request.build())
            .execute()
            .use { response ->
                // Тело читаем только у ошибок: у 2xx это может быть мегабайт потока.
                val body = if (response.code in 200..299) "" else response.body?.string()?.take(160).orEmpty()
                ProbeResult(response.code, body.replace(Regex("\\s+"), " ").trim())
            }
    }.getOrDefault(ProbeResult(-1, ""))

class BraveNewPipeDownloaderImpl(
    proxy: Proxy?,
) : Downloader() {
    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy)
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val response = client.newCall(buildOkHttpRequest(request)).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        val body = response.body.string()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            body,
            response.request.url.toString(),
        )
    }

    private fun buildOkHttpRequest(request: Request): okhttp3.Request {
        val builder =
            okhttp3.Request
                .Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
                .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        request.headers().forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                builder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    builder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                builder.header(headerName, headerValueList[0])
            }
        }
        return builder.build()
    }
}
