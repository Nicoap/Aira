package com.wavora.app.ui.component

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent

/** Desktop actual: ПКМ, колесо мыши и наведение через события указателя AWT/Skiko. */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onSecondaryClick(action: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        if (event.buttons.isSecondaryPressed) {
            event.changes.forEach { it.consume() }
            action()
        }
    }

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onScrollWheel(onScroll: (Float) -> Unit): Modifier =
    onPointerEvent(PointerEventType.Scroll) { event ->
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy != 0f) {
            onScroll(dy)
            event.changes.forEach { it.consume() }
        }
    }

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onHoverMove(
    onMove: (Offset) -> Unit,
    onExit: () -> Unit,
): Modifier =
    onPointerEvent(PointerEventType.Move) { event ->
        event.changes.firstOrNull()?.position?.let(onMove)
    }.onPointerEvent(PointerEventType.Exit) { onExit() }
