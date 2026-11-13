package com.wavora.scraper.extractor

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * YouTube answers LOGIN_REQUIRED ("Sign in to confirm you're not a bot") for every anonymous client when the
 * exit IP (typically a VPN) is flagged. Retrying only adds requests from the flagged IP, so after the first such
 * answer extraction pauses for a few minutes and fails fast with an explanatory message.
 */
object BotBlock {
    const val MESSAGE = "YouTube \u043f\u0440\u043e\u0441\u0438\u0442 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044c, \u0447\u0442\u043e \u0432\u044b \u043d\u0435 \u0431\u043e\u0442: IP \u0438\u043b\u0438 VPN \u043f\u043e\u043c\u0435\u0447\u0435\u043d\u044b. \u0421\u043c\u0435\u043d\u0438\u0442\u0435 \u0441\u0435\u0440\u0432\u0435\u0440 VPN \u0438\u043b\u0438 \u043d\u0430\u043f\u0440\u0430\u0432\u044c\u0442\u0435 youtube.com \u0438 googlevideo.com \u043d\u0430\u043f\u0440\u044f\u043c\u0443\u044e."

    private val PAUSE: Duration = 3.minutes

    @kotlin.concurrent.Volatile
    private var until: TimeSource.Monotonic.ValueTimeMark? = null

    fun active(): Boolean = until?.let { !it.hasPassedNow() } ?: false

    fun trip() {
        if (!active()) until = TimeSource.Monotonic.markNow() + PAUSE
    }

    /** True for the anti-bot answer (not for per-video LOGIN_REQUIRED such as the age check). */
    fun isBotCheck(message: String?): Boolean =
        message != null &&
            (message.contains("not a bot", ignoreCase = true) || message.contains("blocked anonymous", ignoreCase = true))
}