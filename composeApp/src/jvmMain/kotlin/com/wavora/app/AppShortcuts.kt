package com.wavora.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.app.viewModel.UIEvent

// Volume to go back to when M un-mutes.
private var volumeBeforeMute = 0.5f

/**
 * App-wide keyboard shortcuts for the main window.
 *
 * Wired to the window's onKeyEvent, which only receives events that nothing focused consumed:
 * a text field (search, rename...) keeps Space/arrows/letters for typing, so these only fire
 * when you are not typing.
 *
 *   Space        play / pause
 *   Left / Right seek back / forward
 *   N / P        next / previous track
 *   Up / Down    volume +5% / -5%
 *   M            mute / unmute
 *   L            like / unlike the current track
 *   Ctrl+K, Ctrl+F  open Search (cursor goes straight into the field)
 *
 * Media keys and the Windows media overlay are handled separately (WindowsSystemMediaSession).
 */
internal fun handleAppShortcut(
    event: KeyEvent,
    sharedViewModel: SharedViewModel,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && (event.key == Key.K || event.key == Key.F)) {
        AppCommands.openSearch.tryEmit(Unit)
        return true
    }
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false

    return when (event.key) {
        Key.Spacebar -> {
            sharedViewModel.onUIEvent(UIEvent.PlayPause)
            true
        }

        Key.DirectionLeft -> {
            sharedViewModel.onUIEvent(UIEvent.Backward)
            true
        }

        Key.DirectionRight -> {
            sharedViewModel.onUIEvent(UIEvent.Forward)
            true
        }

        Key.N -> {
            sharedViewModel.onUIEvent(UIEvent.Next)
            true
        }

        Key.P -> {
            sharedViewModel.onUIEvent(UIEvent.Previous)
            true
        }

        Key.DirectionUp, Key.DirectionDown -> {
            val step = if (event.key == Key.DirectionUp) 0.05f else -0.05f
            val volume = sharedViewModel.controllerState.value.volume
            sharedViewModel.onUIEvent(UIEvent.UpdateVolume((volume + step).coerceIn(0f, 1f)))
            true
        }

        Key.M -> {
            val volume = sharedViewModel.controllerState.value.volume
            if (volume > 0f) {
                volumeBeforeMute = volume
                sharedViewModel.onUIEvent(UIEvent.UpdateVolume(0f))
            } else {
                sharedViewModel.onUIEvent(UIEvent.UpdateVolume(volumeBeforeMute.coerceAtLeast(0.1f)))
            }
            true
        }

        Key.L -> {
            sharedViewModel.onUIEvent(UIEvent.ToggleLike)
            true
        }

        else -> false
    }
}