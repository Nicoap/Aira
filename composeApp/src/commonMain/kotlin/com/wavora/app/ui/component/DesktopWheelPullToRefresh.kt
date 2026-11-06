package com.wavora.app.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.wavora.app.Platform
import com.wavora.app.getPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Desktop mouse-wheel support for Material3 PullToRefresh.
 *
 * On touch devices the refresh is triggered when the finger is RELEASED (onPreFling).
 * A mouse wheel never produces a release/fling, so on desktop the indicator animated while
 * scrolling up at the top of the list but onRefresh was never called, and the indicator
 * stayed half-visible.
 *
 * This watches the pull distance:
 *  - reached the threshold (distanceFraction >= 1)  -> trigger [onRefresh];
 *  - wheel stopped before the threshold             -> hide the indicator again.
 *
 * No-op on non-desktop platforms (the built-in gesture handling works there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopWheelPullToRefresh(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    if (getPlatform() != Platform.Desktop) return
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(state, isRefreshing) {
        if (isRefreshing) return@LaunchedEffect
        // Right after a refresh finishes the indicator can still sit at 1f: the old code saw that value
        // as a fresh pull and refreshed again, forever (~1 refresh/s measured in the log, each one
        // = 4 YouTube requests + a full parse). Hide it and only fire after the fraction was seen
        // below the threshold again ("armed").
        if (state.distanceFraction >= 1f) state.animateToHidden()
        var armed = state.distanceFraction < 1f
        snapshotFlow { state.distanceFraction }.collectLatest { fraction ->
            if (fraction < 1f) armed = true
            if (fraction <= 0f) return@collectLatest
            if (fraction >= 1f) {
                if (armed) {
                    armed = false
                    currentOnRefresh()
                }
            } else {
                // Wheel stopped without reaching the threshold: no release event will come.
                delay(450)
                state.animateToHidden()
            }
        }
    }
}