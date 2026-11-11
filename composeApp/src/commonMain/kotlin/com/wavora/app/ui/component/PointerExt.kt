package com.wavora.app.ui.component

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent

/**
 * Desktop context menu: runs [action] when the right mouse button is pressed on this element.
 * Touch input never reports a secondary button, so this is a no-op on Android.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onSecondaryClick(action: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        if (event.buttons.isSecondaryPressed) {
            event.changes.forEach { it.consume() }
            action()
        }
    }