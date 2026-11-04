package com.wavora.media_jvm_ui.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.wavora.media_jvm.VlcVideoFrameSurface
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Draws frames coming from a [VlcVideoFrameSurface] straight onto a Compose canvas.
 *
 * Unlike the previous SwingPanel-based approach this is a regular composable:
 *  - it respects clipping, layout, z-order and window resizing (no overlap with other
 *    windows or UI, vertical videos can't break the layout);
 *  - it survives being removed from / re-added to the composition (side panel closed and
 *    reopened) because nothing is re-parented, the surface keeps receiving frames;
 *  - new frames only invalidate the DRAW phase (no recomposition per frame).
 *
 * @param crop true = fill the box and crop the overflow (Canvas backdrop), false = fit
 *   inside the box keeping the aspect ratio (letterbox/pillarbox).
 */
@Composable
fun VlcVideoFrame(
    surface: VlcVideoFrameSurface,
    modifier: Modifier = Modifier,
    crop: Boolean = false,
    background: Color = Color.Black,
) {
    val version by surface.frameVersion.collectAsState()
    val holder = remember(surface) { FrameHolder() }
    DisposableEffect(holder) {
        onDispose { holder.close() }
    }

    Canvas(
        modifier =
            modifier
                .clipToBounds()
                .background(background),
    ) {
        // Reading `version` here (draw phase) subscribes only the draw layer to new frames.
        val image = holder.update(surface, version) ?: return@Canvas
        val imgW = image.width.toFloat()
        val imgH = image.height.toFloat()
        if (imgW <= 0f || imgH <= 0f || size.width <= 0f || size.height <= 0f) return@Canvas

        val scale =
            if (crop) {
                max(size.width / imgW, size.height / imgH)
            } else {
                min(size.width / imgW, size.height / imgH)
            }
        val dstW = imgW * scale
        val dstH = imgH * scale
        val dstX = (size.width - dstW) / 2f
        val dstY = (size.height - dstH) / 2f

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawImageRect(
                image,
                Rect.makeWH(imgW, imgH),
                Rect.makeXYWH(dstX, dstY, dstW, dstH),
            )
        }
    }
}

private class FrameHolder {
    private var image: Image? = null
    private var lastVersion = -1L

    fun update(
        surface: VlcVideoFrameSurface,
        version: Long,
    ): Image? {
        if (version != lastVersion || image == null) {
            val fresh =
                surface.readFrame { pixels, w, h ->
                    Image.makeRaster(
                        ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                        pixels,
                        w * 4,
                    )
                }
            if (fresh != null) {
                image?.close()
                image = fresh
                lastVersion = version
            }
        }
        return image
    }

    fun close() {
        image?.close()
        image = null
    }
}
