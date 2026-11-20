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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.runtime.collectAsState
import com.wavora.app.ui.theme.AppTheme
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.domain.manager.DataStoreManager
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.system_app_icon
import java.util.prefs.Preferences

private const val PREF_EXPAND_ON_HOVER = "islandExpandOnHover"

// Hover mode: a short intent delay before opening, a longer grace period before closing.
private const val HOVER_OPEN_DELAY_MS = 90L
private const val HOVER_CLOSE_DELAY_MS = 380L

// Click mode: the island closes by itself once the pointer has been away for a while.
private const val CLICK_MODE_CLOSE_DELAY_MS = 1600L

/**
 * The mini player as a "dynamic island": a capsule hanging from the top edge of the screen (centred), which
 * opens into a full player on hover or on click, depending on the mode chosen in the island itself.
 *
 * It is a dialog-type window: always on top, without a title bar, not focusable (using it never takes the
 * keyboard away from the game or app underneath) and without a taskbar button. The window is as small as the
 * capsule at rest, so it does not block the screen area around it; while it is open (and during the closing
 * animation) the window is the size of the opened player and the island animates inside it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MiniPlayerWindow(
    sharedViewModel: SharedViewModel,
    onCloseRequest: () -> Unit,
) {
    val prefs = remember { Preferences.userRoot().node("Aira/MiniPlayer") }
    var expandOnHover by remember { mutableStateOf(prefs.getBoolean(PREF_EXPAND_ON_HOVER, true)) }
    var expanded by remember { mutableStateOf(false) }
    var hovering by remember { mutableStateOf(false) }
    var windowBig by remember { mutableStateOf(false) }

    val dialogState =
        rememberDialogState(
            position = WindowPosition(Alignment.TopCenter),
            size = IslandCompactSize,
        )

    // Open/close policy.
    LaunchedEffect(hovering, expandOnHover) {
        if (expandOnHover) {
            if (hovering) {
                delay(HOVER_OPEN_DELAY_MS)
                expanded = true
            } else {
                delay(HOVER_CLOSE_DELAY_MS)
                expanded = false
            }
        } else if (!hovering && expanded) {
            delay(CLICK_MODE_CLOSE_DELAY_MS)
            expanded = false
        }
    }

    // The window grows at once when the island opens and shrinks only after the closing animation is over.
    LaunchedEffect(expanded) {
        if (expanded) {
            windowBig = true
        } else {
            delay(IslandAnimationMs + 80L)
            windowBig = false
        }
    }
    LaunchedEffect(windowBig) {
        dialogState.size = if (windowBig) IslandExpandedSize else IslandCompactSize
        dialogState.position = WindowPosition(Alignment.TopCenter)
    }

    val dataStoreManager: DataStoreManager = koinInject()
    val themeMode by dataStoreManager.themeMode.collectAsState(DataStoreManager.THEME_MODE_DARK)
    val themePrimaryColor by dataStoreManager.themePrimaryColor.collectAsState("#FDC5E2")
    val themeBackgroundColor by dataStoreManager.themeBackgroundColor.collectAsState("#0F0E27")
    val themeSurfaceColor by dataStoreManager.themeSurfaceColor.collectAsState("#06050F")
    val themeTextColor by dataStoreManager.themeTextColor.collectAsState("#F2F2F2")
    val themeSecondaryTextColor by dataStoreManager.themeSecondaryTextColor.collectAsState("auto")
    val islandThemeEnabled by dataStoreManager.miniPlayerThemeEnabled.collectAsState(false)
    val islandBackground by dataStoreManager.miniPlayerBackgroundColor.collectAsState("auto")
    val islandAccent by dataStoreManager.miniPlayerAccentColor.collectAsState("auto")

    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = dialogState,
        title = "Wavora - Mini Player",
        icon = painterResource(Res.drawable.system_app_icon),
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        AppTheme(
            mode = themeMode,
            primaryColorHex = themePrimaryColor,
            backgroundColorHex = themeBackgroundColor,
            surfaceColorHex = themeSurfaceColor,
            textColorHex = themeTextColor,
            secondaryTextColorHex = themeSecondaryTextColor,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onPointerEvent(PointerEventType.Enter) { hovering = true }
                        .onPointerEvent(PointerEventType.Exit) { hovering = false },
                contentAlignment = Alignment.TopCenter,
            ) {
                DynamicIsland(
                    sharedViewModel = sharedViewModel,
                    palette =
                        IslandPalette.resolve(
                            customBackground = islandBackground.takeIf { islandThemeEnabled },
                            customAccent = islandAccent.takeIf { islandThemeEnabled },
                        ),
                    expanded = expanded,
                    expandOnHover = expandOnHover,
                    onToggleExpandMode = {
                        expandOnHover = !expandOnHover
                        prefs.putBoolean(PREF_EXPAND_ON_HOVER, expandOnHover)
                    },
                    onExpandClick = { expanded = true },
                    onClose = onCloseRequest,
                )
            }
        }
    }
}
