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
private const val CHUNK_ATTEMPTS = 2
private const val RETRY_DELAY_MS = 300L

/** Размер окна при перекладывании байтов из ответа googlevideo в ответ плееру. */
private const val IO_WINDOW = 64 * 1024

/**
 * Окна, которыми пробуем взять кусок: на крупный диапазон сервер чаще отвечает 403,
 * а тот же участок мелким окном отдаёт нормально. Раньше 403 на 1 МиБ обрывал поток
 * посреди трека — из-за этого музыка и видео «заедали».
 */
private val CHUNK_WINDOWS = longArrayOf(CHUNK_SIZE, 256L * 1024, 64L * 1024)

/** Мелкий первый кусок: нужен, чтобы картинка/звук появились как можно раньше. */
private const val FIRST_BYTE_CHUNK = 64L * 1024

/**
 * Минимальная суммарная полоса HLS-варианта, пригодного для музыки.
 *
 * Измерено на реальном манифесте: варианты 144p и 240p несут AAC 50 кбит/с, а 360p и все
 * старшие — AAC-LC 131 кбит/с (звук у них одинаковый). Поэтому для аудио берём самый лёгкий
 * вариант не ниже порога: 360p ≈ 157 кбит/с суммарно — почти как прогрессивный opus.
 */
private const val AUDIO_MIN_BANDWIDTH = 150_000L

/**
 * JVM-реализация: подставляет локальный прокси вместо прямого googlevideo-адреса.
 *
 * libVLC запрашивает поток открытым диапазоном (`Range: bytes=0-`), а сервер на такой запрос отвечает
 * 403 даже для живых ссылок. Ограниченные диапазоны (`bytes=0-1048575`) отдаются нормально, поэтому
 * прокси режет запрос плеера на цепочку кусков по 1 МиБ и отдаёт их как один непрерывный ответ.
 */
actual fun rewriteStreamUrl(url: String): String = StreamProxy.localize(url)

actual fun rewriteStreamUrlWithHint(url: String, hint: String?): String = StreamProxy.localize(url, hint)

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
    fun localize(
        url: String,
        hint: String? = null,
    ): String {
        if (url.isBlank()) return url
        val base = baseUrl() ?: return url
        // Хинт, который уже сидит в ссылке, важнее отсутствующего аргумента: пересборка
        // ссылки на новый порт не должна терять выбор варианта HLS.
        val effectiveHint = hint ?: hintOf(url)
        if (url.startsWith(base)) {
            return if (effectiveHint == null || url.contains("h=$effectiveHint")) url else swapHint(url, effectiveHint)
        }
        // Ссылка из прошлого запуска указывает на закрытый порт — пересобираем на текущем.
        val upstream = unwrap(url) ?: url
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(upstream.toByteArray())
        val tail = effectiveHint?.let { "?h=$it" }.orEmpty()
        return "$base${PATH_PREFIX.trimStart('/')}$encoded$tail"
    }

    private val proxyLink = Regex("^http://127\\.0\\.0\\.1:\\d+/stream/([A-Za-z0-9_-]+)(\\?.*)?$")

    private val hintParam = Regex("[?&]h=([A-Za-z0-9:_-]+)")

    /** Хинт выбора варианта HLS (`audio`, `video:720`), зашитый в ссылку прокси. */
    private fun hintOf(url: String): String? = hintParam.find(url)?.groupValues?.get(1)

    /** Часть хинта, отвечающая за выбор варианта: `audio` / `video:<height>` без технических полей. */
    private fun variantHintOf(hint: String?): String? = hint?.substringBefore('|')?.takeIf { it.isNotBlank() }

    private fun swapHint(url: String, hint: String): String {
        val without = url.replace(Regex("[?&]h=[A-Za-z0-9:_-]+"), "")
        val separator = if (without.contains('?')) '&' else '?'
        return "$without${separator}h=$hint"
    }

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
            // Хинт выбора варианта HLS мог приехать и в query, и в хвост пути (к ссылке
            // дописывают `&cpn=...`/`&range=...`), поэтому ищем по всей строке запроса.
            val hint = hintOf(exchange.requestURI.toString())
            if (isHlsManifest(upstream)) {
                serveManifest(exchange, upstream, hint)
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

    private fun isHlsManifest(upstream: String): Boolean =
        upstream.contains("hls_variant") ||
            upstream.contains("/api/manifest/") ||
            upstream.contains(".m3u8")

    /**
     * Отдаёт мастер-манифест HLS, оставляя в нём ровно один вариант.
     *
     * WHY: googlevideo на этом IP отдаёт не больше ~1 МиБ с одной ссылки, а у HLS своя
     * подпись у каждого сегмента (~100 КБ), поэтому поток не обрывается на 59-й секунде.
     * Мастер перечисляет варианты от 157 кбит/с до 4 Мбит/с: для музыки берём самый лёгкий,
     * для видео — ближайший к выбранному качеству.
     */
    private fun serveManifest(
        exchange: HttpExchange,
        upstream: String,
        hint: String?,
    ) {
        val body =
            runCatching {
                client
                    .newCall(
                        Request
                            .Builder()
                            .url(upstream)
                            // Мастер-манифест отдаётся только браузерному User-Agent.
                            .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                            .header("Referer", REFERER)
                            .build(),
                    ).execute()
                    .use { response ->
                        if (response.code !in 200..299) {
                            Logger.w(TAG, "HLS manifest failed: HTTP ${response.code}")
                            null
                        } else {
                            response.body.string()
                        }
                    }
            }.getOrNull()
        if (body == null) {
            respondText(exchange, 502, "manifest unavailable")
            return
        }
        val variants = parseVariants(body)
        // Без хинта отдаём самый лёгкий вариант, а не весь мастер: адаптивный выбор libVLC
        // переключает варианты прямо на сегментах, и каждый переход пересоздаёт видеовыход —
        // со стороны это выглядит как «заедает и играет только первый загруженный кусок».
        val chosen = chooseVariant(variants, variantHintOf(hint) ?: "audio")
        val text =
            if (chosen == null) {
                body
            } else {
                Logger.d(TAG, "HLS variant for hint=$hint: ${chosen.shortSide}p ${chosen.bandwidth / 1000} kbps")
                "#EXTM3U\n#EXT-X-INDEPENDENT-SEGMENTS\n${chosen.attributes}\n${chosen.uri}\n"
            }
        val bytes = text.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private class HlsVariant(
        val attributes: String,
        val uri: String,
        val bandwidth: Long,
        val width: Int,
        val height: Int,
    ) {
        /**
         * Короткая сторона кадра. Качество сравниваем именно по ней: у портретных роликов
         * `RESOLUTION` выглядит как 1080x1920, и фильтр «высота <= 720» не находил ни одного
         * варианта — в итоге libVLC получал весь список и скакал по нему на каждом сегменте.
         */
        val shortSide: Int
            get() = if (width in 1 until height) width else height
    }

    private fun parseVariants(manifest: String): List<HlsVariant> {
        val lines = manifest.lines().map { it.trim() }
        val variants = mutableListOf<HlsVariant>()
        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val uri =
                lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    ?: return@forEachIndexed
            val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
            val width = resolution?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val height = resolution?.groupValues?.get(2)?.toIntOrNull() ?: 0
            variants += HlsVariant(line, uri, bandwidth, width, height)
        }
        return variants
    }

    private fun chooseVariant(
        variants: List<HlsVariant>,
        hint: String,
    ): HlsVariant? {
        if (variants.isEmpty()) return null
        if (hint.startsWith("video:")) {
            val target = hint.removePrefix("video:").filter(Char::isDigit).toIntOrNull() ?: 720
            // Ближайший к выбранному качеству, но не выше него. Если все варианты выше
            // цели (портретное видео), берём самый лёгкий из них, а не весь список.
            return variants.filter { it.shortSide in 1..target }.maxByOrNull { it.shortSide }
                ?: variants.minByOrNull { it.bandwidth }
        }
        // Для звука берём самый лёгкий вариант с полноценным AAC, а не облегчённым 50 кбит/с.
        return variants.filter { it.bandwidth >= AUDIO_MIN_BANDWIDTH }.minByOrNull { it.bandwidth }
            ?: variants.maxByOrNull { it.bandwidth }
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

        val first =
            // Первый кусок берём маленьким и без повторов: libVLC покажет кадр только
            // после первых байтов, а полный мегабайт с ретраями давал старт в 5-9 секунд.
            fetchChunk(upstream, start, start + FIRST_BYTE_CHUNK - 1, attempts = 1)
                ?: fetchWindow(upstream, start, knownTotal?.minus(1) ?: Long.MAX_VALUE)
                ?: return respondText(exchange, 502, "stream unavailable")

        val total = first.total ?: knownTotal
        val headers = exchange.responseHeaders
        headers.add("Accept-Ranges", "bytes")
        first.contentType?.let { headers.add("Content-Type", it) }

        // Сколько байт нужно отдать плееру: до конца файла или до явно запрошенного конца.
        val lastByte =
            if (total != null) {
                if (range.end != null) minOf(range.end, total - 1) else total - 1
            } else {
                null
            }
        val remaining = if (lastByte != null) (lastByte - start + 1).coerceAtLeast(0L) else -1L

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
            var chunk: ChunkResponse = first
            var nextStart = start
            var left = remaining
            while (true) {
                var delivered = 0L
                var clientDone = false
                chunk.response.use { response ->
                    val input = response.body.byteStream()
                    if (input == null) {
                        clientDone = true
                        return@use
                    }
                    val buffer = ByteArray(IO_WINDOW)
                    while (left != 0L) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val toWrite = if (left > 0 && read > left) left.toInt() else read
                        out.write(buffer, 0, toWrite)
                        delivered += toWrite
                        if (left > 0) left -= toWrite
                        if (toWrite < read || left == 0L) {
                            clientDone = true
                            break
                        }
                    }
                }
                nextStart += delivered
                if (clientDone || delivered <= 0L) break
                if (lastByte != null && nextStart > lastByte) break
                // Продолжаем с того байта, на котором реально остановились: окно могло
                // оказаться меньше запрошенного, а недокачанный кусок не должен теряться.
                chunk =
                    fetchWindow(upstream, nextStart, lastByte ?: (nextStart + CHUNK_SIZE - 1))
                        ?: break
            }
        }
    }

    /**
     * Достаёт кусок [from, limit], перебирая окна разного размера: если крупный диапазон
     * получил 403, тот же участок мелким окном обычно отдаётся нормально.
     */
    private fun fetchWindow(upstream: String, from: Long, limit: Long): ChunkResponse? {
        if (from > limit) return null
        for (window in CHUNK_WINDOWS) {
            val to = minOf(from + window - 1, limit)
            val chunk = fetchChunk(upstream, from, to)
            if (chunk != null) return chunk
        }
        return null
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
        // Ссылка со встроенным `range=0-N`: googlevideo отдаёт только первые N байт,
        // а всё, что дальше, отвечает 403. Именно из-за этого трек обрывался на одной
        // и той же секунде при каждом воспроизведении.
        val cleanUpstream by lazy { withoutUrlRange(upstream) }
        repeat(attempts) { attempt ->
            // Первую попытку делаем со ссылкой как есть (она рабочая для первых байт),
            // а повторы — уже без встроенного диапазона.
            val target = if (attempt == 0) upstream else cleanUpstream
            val request =
                Request
                    .Builder()
                    .url(target)
                    .header("Range", "bytes=$from-$to")
                    // Плеерный User-Agent сервер не принимает, запрашиваем как веб-клиент.
                    .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                    .header("Referer", REFERER)
                    .header("Origin", REFERER)
                    .build()
            val result = runCatching { client.newCall(request).execute() }
            result.exceptionOrNull()?.let { lastError = it as? Exception }
            val response = result.getOrNull()
            if (response != null) {
                if (response.code in 200..299) return response.toChunk()
                val code = response.code
                if (code == 416) {
                    // Запросили байты за концом файла — это штатный конец потока, а не сбой.
                    response.close()
                    return null
                }
                // Тело ответа объясняет отказ (истёкшая подпись, лимит, PO-token) — без него
                // в логах видно только «HTTP 403» и причину не найти.
                val detail =
                    runCatching {
                        val text = response.body.string().replace('\n', ' ').trim()
                        response.header("Retry-After")?.let { "retry-after $it: $text" } ?: text
                    }.getOrNull()
                response.close()
                lastError = IOException("HTTP $code${detail?.takeIf { it.isNotBlank() }?.let { ": ${it.take(200)}" } ?: ""}")
            }
            if (attempt + 1 < attempts) Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
        }
        Logger.w(TAG, "Stream chunk $from-$to failed (client=${clientTag(upstream)}): ${lastError?.message?.take(200)}")
        return null
    }

    /**
     * Убирает из ссылки параметры `range`, `rn` и `rbuf`.
     *
     * WHY: экстрактор иногда отдаёт ссылку, уже ограниченную первым сегментом
     * (`range=0-1114111`). Прокси и так запрашивает нужный кусок заголовком `Range`,
     * а встроенный параметр сервер понимает буквально и на всё, что за ним, отвечает 403.
     */
    private fun withoutUrlRange(url: String): String {
        if (!url.contains("range=") && !url.contains("rn=") && !url.contains("rbuf=")) return url
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val kept =
            url.substring(queryStart + 1)
                .split('&')
                .filterNot { param ->
                    param.startsWith("range=") || param.startsWith("rn=") || param.startsWith("rbuf=")
                }
        return if (kept.isEmpty()) url.substring(0, queryStart) else "${url.substring(0, queryStart)}?${kept.joinToString("&")}"
    }

    /** Встроенный в ссылку `range` (для логов) — обычно и есть причина 403. */
    private fun urlRangeNote(url: String): String =
        Regex("[?&]range=([^&]*)").find(url)?.groupValues?.getOrNull(1)?.let { ", url range=0-$it" } ?: ""

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
