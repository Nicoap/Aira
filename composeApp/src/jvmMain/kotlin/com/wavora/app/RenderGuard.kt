package com.wavora.app

import java.io.File

/**
 * Chooses the Skia render API and protects against GPU drivers that crash on startup.
 *
 * Flow:
 *  - By default the GPU backend (DIRECT3D on Windows) is used. Software (CPU) rendering
 *    was forced before and was the main reason for laggy UI, especially with big
 *    surfaces like the side panel.
 *  - Before the window starts a marker file is written. Once the main window has drawn a
 *    few real frames, [confirmRendered] deletes it.
 *  - If the marker is still there on the next launch, the previous launch died before
 *    rendering (driver crash) -> SOFTWARE is forced from then on (flag file).
 *
 * Manual override with the environment variable WAVORA_RENDER:
 *  - `software`          : always CPU rendering
 *  - `direct3d`/`gpu`    : always GPU rendering and forget any stored fallback
 */
internal object RenderGuard {
    private val dir = File(System.getProperty("user.home"), ".wavora")
    private val pending = File(dir, "render_pending")
    private val forceSoftware = File(dir, "render_force_software")

    @Volatile
    private var usingGpu = false

    fun chooseRenderApi(): String {
        try {
            dir.mkdirs()
            when (System.getenv("WAVORA_RENDER")?.lowercase()) {
                "software" -> return "SOFTWARE"
                "direct3d", "gpu", "hardware" -> forceSoftware.delete()
            }
            if (pending.exists()) {
                // Previous launch never produced a rendered frame.
                forceSoftware.createNewFile()
            }
            if (forceSoftware.exists()) {
                pending.delete()
                return "SOFTWARE"
            }
            usingGpu = true
            return "DIRECT3D"
        } catch (_: Throwable) {
            return "SOFTWARE"
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
        } catch (_: Throwable) {
        }
    }

    fun confirmRendered() {
        try {
            pending.delete()
        } catch (_: Throwable) {
        }
    }
}
