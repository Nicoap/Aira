package com.wavora.app.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/* iOS actual: указателя мыши нет — ПКМ, колесо и наведение ничего не делают. */

actual fun Modifier.onSecondaryClick(action: () -> Unit): Modifier = this

actual fun Modifier.onScrollWheel(onScroll: (Float) -> Unit): Modifier = this

actual fun Modifier.onHoverMove(
    onMove: (Offset) -> Unit,
    onExit: () -> Unit,
): Modifier = this
