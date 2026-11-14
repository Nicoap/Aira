package com.wavora.app.ui.mini_player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.app.viewModel.UIEvent
import org.jetbrains.compose.resources.painterResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.system_app_icon

import java.util.prefs.Preferences

/**
 * Mini player window - a separate always-on-top window for music controls.
 *
 * Now shaped like a "dynamic island": a small pill when idle (cover, title,
 * play/pause + next), expanding on hover into the full mini player with
 * artwork, seek bar and the rest of the controls. The window is not resizable
 * and its size is driven entirely by [expanded], so it always looks the same
 * on every machine and nothing can be dragged into a broken layout.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MiniPlayerWindow(
    sharedViewModel: SharedViewModel,
    onCloseRequest: () -> Unit,
) {
    val prefs = remember { Preferences.userRoot().node("Wavora/MiniPlayer") }

    // Island sizes in dp: idle pill vs hovered panel.
    val compactSize = DpSize(340.dp, 72.dp)
    val expandedSize = DpSize(460.dp, 188.dp)

    // Load saved position or use default
    val savedX = prefs.getFloat("windowX", Float.NaN)
    val savedY = prefs.getFloat("windowY", Float.NaN)

    var expanded by remember { mutableStateOf(false) }

    var windowState by remember {
        mutableStateOf(
            WindowState(
                placement = WindowPlacement.Floating,
                position =
                    if (savedX.isNaN() || savedY.isNaN()) {
                        WindowPosition(Alignment.BottomEnd)
                    } else {
                        WindowPosition(savedX.coerceAtLeast(0f).dp, savedY.coerceAtLeast(0f).dp)
                    },
                size = compactSize,
            ),
        )
    }

    // Island behaviour: keep the centre in place while growing/shrinking, so the
    // pill does not jump to the side when the pointer enters it.
    LaunchedEffect(expanded) {
        val target = if (expanded) expandedSize else compactSize
        val current = windowState.size
        if (current != target) {
            val position = windowState.position
            if (position is WindowPosition.Absolute) {
                windowState.position =
                    WindowPosition(
                        position.x + (current.width - target.width) / 2,
                        position.y + (current.height - target.height) / 2,
                    )
            }
            windowState.size = target
        }
    }

    // Save position only: the size is owned by the island above.
    LaunchedEffect(windowState.position) {
        val pos = windowState.position
        if (pos is WindowPosition.Absolute) {
            prefs.putFloat("windowX", pos.x.value)
            prefs.putFloat("windowY", pos.y.value)
        }
    }

    // WAVORA FIX: transparent + undecorated windows are invisible on Windows
    // with integrated or older GPU drivers. Use native decoration on Windows.
    val isWindows = remember {
        System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Wavora - Mini Player",
        icon = painterResource(Res.drawable.system_app_icon),
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
        state = windowState,
        onKeyEvent = { keyEvent ->
            when {
                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Spacebar -> {
                    sharedViewModel.onUIEvent(UIEvent.PlayPause)
                    true
                }

                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight -> {
                    sharedViewModel.onUIEvent(UIEvent.Next)
                    true
                }

                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft -> {
                    sharedViewModel.onUIEvent(UIEvent.Previous)
                    true
                }

                else -> {
                    false
                }
            }
        },
    ) {
        // Остров: в покое это компактная пилюля, при наведении раскрывается.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPointerEvent(PointerEventType.Enter) { expanded = true }
                    .onPointerEvent(PointerEventType.Exit) { expanded = false },
        ) {
            MiniPlayerRoot(
                sharedViewModel = sharedViewModel,
                onClose = onCloseRequest,
                windowState = windowState,
                window = window,
            )
        }
    }
}
