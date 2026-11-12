package com.wavora.app

import com.wavora.logger.Logger
import java.io.File

/**
 * Chooses the Skia render API and protects against GPU drivers that crash on startup.
 *
 * Flow:
 *  - By default the GPU backend (DIRECT3D on Windows) is used. Software (CPU) rendering costs
 *    roughly a full CPU core for the UI thread (every frame is rasterised on the CPU) and was a
 *    main cause of laggy UI, especially with big surfaces like the side panel.
 *  - Before the window starts a marker file is written. Once the main window has drawn a
 *    few real frames, [confirmRendered] deletes it. A CLEAN exit before that (window closed,
 *    Ctrl+C, System.exit) also deletes it: that is not a driver crash.
 *  - If the marker is still there on the next launch, the previous launch really died before
 *    rendering (driver crash, killed process) -> SOFTWARE is forced from then on (flag file).
 *  - The chosen API and the reason are written to the log, so a sticky CPU fallback is visible.
 *
 * Manual override with the environment variable WAVORA_RENDER:
 *  - `software`          : always CPU rendering
 *  - `direct3d`/`gpu`    : always GPU rendering and forget any stored fallback
 */
internal object RenderGuard {
    private const val TAG = "RenderGuard"
    private val dir = File(System.getProperty("user.home"), ".wavora")
    private val pending = File(dir, "render_pending")
    private val forceSoftware = File(dir, "render_force_software")

    @Volatile
    private var usingGpu = false

    @Volatile
    private var api = "DIRECT3D"

    @Volatile
    private var reason = "default"

    private fun pick(
        chosen: String,
        why: String,
    ): String {
        api = chosen
        reason = why
        return chosen
    }

    fun chooseRenderApi(): String {
        try {
            dir.mkdirs()
            when (System.getenv("WAVORA_RENDER")?.lowercase()) {
                "software" -> return pick("SOFTWARE", "WAVORA_RENDER=software")
                "direct3d", "gpu", "hardware" -> forceSoftware.delete()
            }
            var why: String? = null
            if (pending.exists()) {
                // Previous launch neither rendered a frame nor exited cleanly.
                forceSoftware.createNewFile()
                why = "previous launch died before its first frame"
            }
            if (forceSoftware.exists()) {
                pending.delete()
                return pick(
                    "SOFTWARE",
                    why
                        ?: "sticky fallback flag ${forceSoftware.name} from an earlier failed launch " +
                        "(delete it or set WAVORA_RENDER=gpu to retry the GPU)",
                )
            }
            usingGpu = true
            return pick("DIRECT3D", "default")
        } catch (_: Throwable) {
            return pick("SOFTWARE", "RenderGuard error")
        }
    }

    /**
     * Call once we know this process is the real (first) instance, so a second launch that
     * exits immediately never leaves a stale marker behind.
     */
    fun markStarting() {
        if (!usingGpu) return
        try {
            pending.writeText(System.currentTimeMillis().toString())
            // A clean exit before the first frame is not a driver crash; only a real crash or a
            // kill leaves the marker behind.
            Runtime.getRuntime().addShutdownHook(Thread { runCatching { pending.delete() } })
        } catch (_: Throwable) {
        }
    }

    fun confirmRendered() {
        try {
            pending.delete()
        } catch (_: Throwable) {
        }
        if (api == "SOFTWARE") {
            Logger.w(TAG, "Rendering on the CPU (SOFTWARE): $reason")
        } else {
            Logger.i(TAG, "Render API: $api ($reason)")
        }
    }
}
