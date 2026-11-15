package com.wavora.app.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/*
 * Android actual: мыши нет, поэтому ПКМ, колесо и наведение — no-op.
 * Значения оставлены ради общей сигнатуры с desktop-реализацией.
 */

actual fun Modifier.onSecondaryClick(action: () -> Unit): Modifier = this

actual fun Modifier.onScrollWheel(onScroll: (Float) -> Unit): Modifier = this

actual fun Modifier.onHoverMove(
    onMove: (Offset) -> Unit,
    onExit: () -> Unit,
): Modifier = this
