package com.wavora.app.expect.ui

import androidx.compose.ui.graphics.Color
import java.awt.MouseInfo
import java.awt.Robot

actual fun sampleScreenPixel(): Color? =
    runCatching {
        val point = MouseInfo.getPointerInfo()?.location ?: return@runCatching null
        val pixel = Robot().getPixelColor(point.x, point.y)
        Color(pixel.red, pixel.green, pixel.blue)
    }.getOrNull()
