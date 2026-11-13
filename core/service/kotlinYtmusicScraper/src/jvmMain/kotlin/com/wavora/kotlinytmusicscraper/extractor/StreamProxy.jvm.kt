package com.wavora.scraper.extractor

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.wavora.logger.Logger
import com.wavora.scraper.models.YouTubeClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "StreamProxy"

/** Размер куска, который запрашивается у googlevideo. Открытый `bytes=0-` сервер отдаёт 403. */
private const val CHUNK_SIZE = 1L shl 20

private const val PATH_PREFIX = "/stream/"
private const val REFERER = "https://music.youtube.com/"
private const val CHUNK_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 300L

/** Размер окна при перекладывании байтов из ответа googlevideo в ответ плееру. */
private const val IO_WINDOW = 64 * 1024

/** Окна поменьше, которыми пробуем открыть поток, если крупный диапазон получил 403. */
private val FIRST_CHUNK_FALLBACKS = longArrayOf(256L * 1024, 64L * 1024)

/** Мелкий первый кусок: нужен, чтобы картинка/звук появились как можно раньше. */
private const val FIRST_BYTE_CHUNK = 64L * 1024

/**
 * JVM-реализация: подставляет локальный прокси вместо прямого googlevideo-адреса.
 *
 * libVLC запрашивает поток открытым диапазоном (`Range: bytes=0-`), а сервер на такой запрос отвечает
 * 403 даже для живых ссылок. Ограниченные диапазоны (`bytes=0-1048575`) отдаются нормально, поэтому
 * прокси режет запрос плеера на цепочку кусков по 1 МиБ и отдаёт их как один непрерывный ответ.
 */
actual fun rewriteStreamUrl(url: String): String = StreamProxy.localize(url)

actual fun upstreamStreamUrl(url: String): String = StreamProxy.unwrap(url) ?: url

internal object StreamProxy {
    private val lock = Any()

    @Volatile
    private var server: HttpServer? = null

    @Volatile
    private var baseUrl: String? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Поток качается кусками, пока плеер не закроет соединение — общий таймаут вызова тут вреден.
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Возвращает адрес прокси для переданной ссылки; при любой проблеме — исходную ссылку. */
    fun localize(url: String): String {
        if (url.isBlank()) return url
        val base = baseUrl() ?: return url
        if (url.startsWith(base)) return url
        // A link cached by an earlier run points at that run's (now closed) port: rebuild it on the current one.
        val upstream = unwrap(url) ?: url
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(upstream.toByteArray())
        return "$base${PATH_PREFIX.trimStart('/')}$encoded"
    }

    private val proxyLink = Regex("^http://127\\.0\\.0\\.1:\\d+/stream/([A-Za-z0-9_-]+)$")

    /** Decodes a local proxy link (of any run) back to the upstream googlevideo address; null if it is not one. */
    fun unwrap(url: String): String? {
        val encoded = proxyLink.find(url)?.groupValues?.get(1) ?: return null
        return runCatching { String(Base64.getUrlDecoder().decode(encoded)) }
            .getOrNull()
            ?.takeIf { it.startsWith("http") }
    }

    private fun clientTag(upstream: String): String = Regex("[?&]c=([A-Z_]+)").find(upstream)?.groupValues?.get(1) ?: "?"

    private fun baseUrl(): String? {
        baseUrl?.let { return it }
        synchronized(lock) {
            baseUrl?.let { return it }
            return runCatching { start() }
                .onFailure { Logger.w(TAG, "Stream proxy failed to start: ${it.message?.take(160)}") }
                .getOrNull()
        }
    }

    private fun start(): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.executor =
            Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "Wavora-StreamProxy").apply { isDaemon = true }
            }
        httpServer.createContext("/", ::handle)
        httpServer.start()
        server = httpServer
        val url = "http://127.0.0.1:${httpServer.address.port}/"
        baseUrl = url
        Logger.d(TAG, "Stream proxy listening on $url")
        return url
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            if (!path.startsWith(PATH_PREFIX)) {
                respondText(exchange, 404, "not found")
                return
            }
            // Декодируем только сам base64url-хвост: к ссылке могут прицепить тег вида `&c=CLIENT`,
            // и тогда он попадает в путь (не в query), из-за чего URL-декодер падал на `&`.
            val encoded = path.removePrefix(PATH_PREFIX).substringBefore('&').substringBefore('?')
            val upstream =
                runCatching { String(Base64.getUrlDecoder().decode(encoded)) }
                    .getOrNull()
                    ?.takeIf { it.startsWith("http") }
            if (upstream == null) {
                respondText(exchange, 400, "bad stream id")
                return
            }
            if (exchange.requestMethod.equals("HEAD", true)) {
                // libVLC сначала спрашивает HEAD: сообщаем, что диапазоны поддержаны и длину не знаем.
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.sendResponseHeaders(200, -1)
                return
            }
            stream(exchange, upstream, parseRange(exchange.requestHeaders.getFirst("Range")))
        } catch (e: IOException) {
            // Плеер закрыл соединение (перемотка, смена трека) — это нормальный ход событий.
            Logger.d(TAG, "Stream proxy client went away: ${e.message?.take(120)}")
            runCatching { exchange.close() }
        } catch (e: Exception) {
            Logger.w(TAG, "Stream proxy failed: ${e.message?.take(160)}")
            runCatching { respondText(exchange, 502, "stream failed") }
        }
    }

    private fun stream(exchange: HttpExchange, upstream: String, range: RequestedRange) {
        var start = range.start ?: 0L
        var knownTotal = range.totalHint

        if (range.start == null && range.suffix != null) {
            // `bytes=-N`: сначала узнаём полный размер, потом считаем начало.
            val head = fetchChunk(upstream, 0L, 0L) ?: return respondText(exchange, 502, "stream unavailable")
            val total = head.total
            head.response.close()
            if (total == null) return respondText(exchange, 502, "stream unavailable")
            knownTotal = total
            start = (total - range.suffix).coerceAtLeast(0L)
        }

        var first =
            // Первый кусок берём маленьким и без повторов: libVLC покажет кадр только
            // после первых байтов, а полный мегабайт с ретраями давал старт в 5-9 секунд.
            fetchChunk(upstream, start, start + FIRST_BYTE_CHUNK - 1, attempts = 1)
                ?: fetchChunk(upstream, start, start + CHUNK_SIZE - 1)
        if (first == null) {
            // Часть ссылок отвечает 403 на крупный диапазон, но нормально отдаётся мелкими
            // кусками — пробуем окно поменьше, прежде чем считать поток недоступным.
            for (window in FIRST_CHUNK_FALLBACKS) {
                first = fetchChunk(upstream, start, start + window - 1)
                if (first != null) break
            }
        }
        first ?: return respondText(exchange, 502, "stream unavailable")

        val total = first.total ?: knownTotal
        val headers = exchange.responseHeaders
        headers.add("Accept-Ranges", "bytes")
        first.contentType?.let { headers.add("Content-Type", it) }

        // Сколько байт нужно отдать плееру: до конца файла или до явно запрошенного конца.
        var remaining =
            if (total != null) {
                val last = if (range.end != null) minOf(range.end, total - 1) else total - 1
                (last - start + 1).coerceAtLeast(0L)
            } else {
                -1L // размер неизвестен — отдаём потоком, без Content-Length
            }

        if (total != null && remaining > 0) {
            headers.add("Content-Range", "bytes $start-${start + remaining - 1}/$total")
            val ranged = range.start != null || range.end != null || range.suffix != null
            if (ranged) {
                exchange.sendResponseHeaders(206, remaining)
            } else {
                headers.add("Content-Length", remaining.toString())
                exchange.sendResponseHeaders(200, remaining)
            }
        } else {
            exchange.sendResponseHeaders(200, 0)
        }

        exchange.responseBody.use { out ->
            var chunk: ChunkResponse = first ?: return respondText(exchange, 502, "stream unavailable")
            var nextStart = start
            while (true) {
                var delivered = 0L
                var needNext = true
                chunk.response.use { response ->
                    val input = response.body.byteStream()
                    if (input == null) {
                        needNext = false
                        return@use
                    }
                    val buffer = ByteArray(IO_WINDOW)
                    while (remaining != 0L) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val toWrite = if (remaining > 0 && read > remaining) remaining.toInt() else read
                        out.write(buffer, 0, toWrite)
                        delivered += toWrite
                        if (remaining > 0) remaining -= toWrite
                        if (toWrite < read || remaining == 0L) {
                            needNext = false
                            break
                        }
                    }
                }
                if (!needNext || delivered <= 0L) break
                nextStart += delivered
                if (total != null && nextStart >= total) break
                val nextEnd =
                    if (total != null) minOf(nextStart + CHUNK_SIZE - 1, total - 1) else nextStart + CHUNK_SIZE - 1
                chunk = fetchChunk(upstream, nextStart, nextEnd) ?: break
            }
        }
    }

    /**
     * Запрашивает у googlevideo ограниченный кусок. Открытый диапазон тут использовать нельзя:
     * сервер отвечает на него 403 даже для рабочих ссылок.
     */
    private fun fetchChunk(
        upstream: String,
        from: Long,
        to: Long,
        attempts: Int = CHUNK_ATTEMPTS,
    ): ChunkResponse? {
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            val result =
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url(upstream)
                            .header("Range", "bytes=$from-$to")
                            // Плеерный User-Agent сервер не принимает, запрашиваем как веб-клиент.
                            .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                            .header("Referer", REFERER)
                            .header("Origin", REFERER)
                            .build()
                    val response = client.newCall(request).execute()
                    if (response.code in 200..299) {
                        return response.toChunk()
                    }
                    val code = response.code
                    response.close()
                    if (code == 416) {
                        // Запросили байты за концом файла — это штатный конец потока, а не сбой.
                        return null
                    }
                    throw IOException("HTTP $code")
                }
            result.exceptionOrNull()?.let { lastError = it as? Exception }
            if (result.isSuccess) return result.getOrNull()
            if (attempt + 1 < CHUNK_ATTEMPTS) Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
        }
        Logger.w(TAG, "Stream chunk $from-$to failed (client=${clientTag(upstream)}): ${lastError?.message?.take(120)}")
        return null
    }

    private fun Response.toChunk(): ChunkResponse? {
        val total =
            header("Content-Range")
                ?.substringAfter('/', "")
                ?.takeIf { it.isNotBlank() && it != "*" }
                ?.toLongOrNull()
        return ChunkResponse(code, header("Content-Type"), total, this)
    }

    private fun parseRange(header: String?): RequestedRange {
        if (header.isNullOrBlank()) return RequestedRange(null, null, null)
        val value = header.removePrefix("bytes=").substringBefore(',').trim()
        val dash = value.indexOf('-')
        if (dash < 0) return RequestedRange(null, null, null)
        val start = value.substring(0, dash).trim().toLongOrNull()
        val end = value.substring(dash + 1).trim().toLongOrNull()
        return if (start == null) RequestedRange(null, null, end) else RequestedRange(start, end, null)
    }

    private fun respondText(exchange: HttpExchange, status: Int, message: String) {
        val bytes = message.toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    /** Запрошенный плеером диапазон; `suffix` — это `bytes=-N`, последние N байт. */
    private class RequestedRange(val start: Long?, val end: Long?, val suffix: Long?, val totalHint: Long? = null)

    private class ChunkResponse(
        val code: Int,
        val contentType: String?,
        val total: Long?,
        val response: Response,
    )
}
