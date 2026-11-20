package com.wavora.app.diagnostics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wavora.appdata.io.localAppFolder
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Logging persistente en Desktop (pedido explícito de devgodot: Kermit en JVM
// solo imprime a stdout, así que entre sesiones no queda rastro).
//
// Fork: rotación + filtro de ruido (medido: ~15 МБ и ~17-57k строк за сессию,
// без ротации). Правила:
//  - один файл сессии `wavora-<ts>.log`, при превышении MAX_SEGMENT_BYTES
//    переименовывается в `wavora-<ts>.1.log` (перезаписывается) => <= ~20 МБ на сессию;
//  - при старте остаются только KEEP_SESSIONS последних сессий;
//  - строки длиннее MAX_LINE_CHARS и стектрейсы длиннее MAX_STACK_LINES обрезаются;
//  - заведомо бесполезный шум (см. `noise`) отбрасывается;
//  - WAVORA_LOG_VERBOSE=1 или WAVORA_DIAG=1 отключают фильтрацию шума.
class AuditFileLogWriter : LogWriter() {
    companion object {
        private const val KEEP_SESSIONS = 5
        private const val MAX_SEGMENT_BYTES = 10L * 1024 * 1024
        private const val MAX_LINE_CHARS = 400
        private const val MAX_STACK_LINES = 25
        private const val EARLY_MARKER_TTL_MS = 3L * 24 * 3600 * 1000

        private val verbose: Boolean =
            System.getenv("WAVORA_LOG_VERBOSE") == "1" || System.getenv("WAVORA_DIAG") == "1"

        // Подстроки сообщений, которые не несут диагностической ценности
        // (частоты — по логам 2026-11-04: watchtime ~560, lyrics-miss ~500, Check liked ~130, ...).
        private val noise = listOf(
            "watchtime done",
            "Lyrics found: []",
            "Check liked:",
            "Check recover queue",
            "Backend error not_found",
            "No lyrics found for videoId",
            "getTranslatedLyrics(",
            "Get Translated Lyrics Error",
        )

        val logDir: File by lazy {
            val base = File(localAppFolder(), "logs")
            base.apply { mkdirs() }
        }

        // Un archivo por sesión de la app (no por día), para poder mandar
        // "el log de esta corrida" sin tener que recortarlo a mano.
        val logFile: File by lazy {
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            File(logDir, "aira-$ts.log")
        }

        private fun sessionKey(f: File): String = f.name.removeSuffix(".log").removeSuffix(".1")

        // Secrets must never reach the log file (Canvas used to log the Spotify personal/client tokens and
        // the YouTube SAPISIDHASH at Debug level). Applied to every message before it is written.
        private val secretKeyValue =
            Regex("""(?i)((?:token|secret|password|authorization|cookie)\w*\s*[:=]\s*)(?:Bearer\s+)?[A-Za-z0-9+/_\-.=%]{12,}""")
        private val sapisidHash = Regex("""(SAPISIDHASH\s+)\S+""")

        private fun redact(s: String): String =
            s.replace(secretKeyValue, "$1<redacted>").replace(sapisidHash, "$1<redacted>")
    }

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private var writer: PrintWriter? = null
    private var written = 0L

    init {
        pruneOldSessions()
    }

    private fun pruneOldSessions() {
        runCatching {
            val all = logDir.listFiles()?.filter { it.isFile } ?: return
            val current = sessionKey(logFile)
            all.filter {
                (it.name.startsWith("aira-") || it.name.startsWith("wavora-")) &&
                    it.name.endsWith(".log")
            }
                .groupBy { sessionKey(it) }
                .filterKeys { it != current }
                .entries
                .sortedByDescending { e -> e.value.maxOf { it.lastModified() } }
                .drop(KEEP_SESSIONS - 1)
                .forEach { e -> e.value.forEach { it.delete() } }
            val cutoff = System.currentTimeMillis() - EARLY_MARKER_TTL_MS
            all.filter { it.name.startsWith("early-marker-") && it.lastModified() < cutoff }
                .forEach { it.delete() }
        }
    }

    // autoFlush = true: si el proceso queda "Suspendido" o hay que matarlo a
    // la fuerza, el archivo tiene que seguir teniendo lo escrito hasta ese
    // instante — es la evidencia que se necesita justo en esos casos.
    private fun openWriter(): PrintWriter {
        written = logFile.length()
        return PrintWriter(OutputStreamWriter(FileOutputStream(logFile, true), Charsets.UTF_8), true)
    }

    private fun rotateIfNeeded() {
        if (written < MAX_SEGMENT_BYTES) return
        runCatching { writer?.close() }
        writer = null
        runCatching {
            val rolled = File(logDir, logFile.name.removeSuffix(".log") + ".1.log")
            rolled.delete()
            logFile.renameTo(rolled)
        }
        written = 0L
    }

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        val ts = LocalDateTime.now().format(timeFmt)
        val threadName = Thread.currentThread().name
        val safeMessage = redact(message)
        val body =
            if (safeMessage.length > MAX_LINE_CHARS) {
                safeMessage.take(MAX_LINE_CHARS) + "... (+${safeMessage.length - MAX_LINE_CHARS} chars)"
            } else {
                safeMessage
            }
        val line = "$ts [$threadName] ${severity.name.padEnd(5)} $tag: $body"
        if (!verbose && noise.any { line.contains(it) }) return
        synchronized(this) {
            rotateIfNeeded()
            val w = writer ?: openWriter().also { writer = it }
            w.println(line)
            written += line.length + 2
            throwable?.let {
                val st = it.stackTraceToString().lineSequence().take(MAX_STACK_LINES).joinToString("\n")
                w.println(st)
                written += st.length + 2
            }
        }
    }
}