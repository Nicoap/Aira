package com.wavora.app

import com.wavora.logger.Logger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Diagnostic only (does not change behaviour).
 *
 * Windows reports "Not responding" when the UI thread stops pumping messages for a few seconds.
 * That is hard to catch because it happens in episodes. This watchdog posts a tiny ping to the UI
 * thread (AWT-EventQueue-0) twice a second; if the UI thread does not answer for
 * [STALL_THRESHOLD_MS], the log gets the UI thread's stack at that moment (repeated every
 * [REPEAT_DUMP_MS] while the stall lasts) and a line with the total length once it recovers.
 *
 * It also reports when the watchdog thread itself was suspended (JVM-wide pause: long GC /
 * safepoint, or the machine was asleep), which points at memory pressure instead of the UI.
 *
 * Disable with WAVORA_WATCHDOG=0.
 */
internal object UiStallWatchdog {
    private const val TAG = "UiStallWatchdog"
    private const val PING_INTERVAL_MS = 500L
    private const val STALL_THRESHOLD_MS = 2_500L
    private const val REPEAT_DUMP_MS = 5_000L
    private const val PAUSE_REPORT_MS = 1_000L

    private val lastAckNanos = AtomicLong(System.nanoTime())

    @Volatile
    private var started = false

    fun start() {
        if (started || System.getenv("WAVORA_WATCHDOG") == "0") return
        started = true
        thread(name = "ui-stall-watchdog", isDaemon = true) {
            try {
                loop()
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun loop() {
        var stallStartNanos = 0L
        var lastDumpNanos = 0L
        var lastTickNanos = System.nanoTime()
        while (true) {
            SwingUtilities.invokeLater { lastAckNanos.set(System.nanoTime()) }
            Thread.sleep(PING_INTERVAL_MS)
            val now = System.nanoTime()

            val overshootMs = (now - lastTickNanos) / 1_000_000 - PING_INTERVAL_MS
            lastTickNanos = now
            if (overshootMs >= PAUSE_REPORT_MS) {
                Logger.w(TAG, "Watchdog thread was suspended for ~$overshootMs ms (JVM-wide pause: GC/safepoint, or the system slept)")
            }

            val sinceAckMs = (now - lastAckNanos.get()) / 1_000_000
            if (sinceAckMs >= STALL_THRESHOLD_MS) {
                if (stallStartNanos == 0L) {
                    stallStartNanos = now - sinceAckMs * 1_000_000
                    lastDumpNanos = 0L
                }
                if (lastDumpNanos == 0L || (now - lastDumpNanos) / 1_000_000 >= REPEAT_DUMP_MS) {
                    lastDumpNanos = now
                    Logger.w(TAG, "UI thread has not responded for $sinceAckMs ms. Stack of AWT-EventQueue-0:\n${uiThreadStack()}")
                }
            } else if (stallStartNanos != 0L) {
                Logger.w(TAG, "UI thread responsive again after a stall of ~${(now - stallStartNanos) / 1_000_000} ms")
                stallStartNanos = 0L
            }
        }
    }

    private fun uiThreadStack(): String {
        val entry =
            Thread.getAllStackTraces().entries.firstOrNull { it.key.name.startsWith("AWT-EventQueue") }
                ?: return "(UI thread not found)"
        return "state=${entry.key.state}\n" + entry.value.take(40).joinToString("\n") { "    at $it" }
    }
}
